package com.toopher.pingidentity;

import java.io.IOException;
import java.lang.Object;
import java.lang.String;
import java.math.BigInteger;
import java.security.SecureRandom;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.sourceid.common.ResponseTemplateRenderer;
import org.sourceid.common.Util;
import org.sourceid.saml20.adapter.AuthnAdapterException;
import org.sourceid.saml20.adapter.conf.Configuration;
import org.sourceid.saml20.adapter.gui.AdapterConfigurationGuiDescriptor;
import org.sourceid.saml20.adapter.gui.TextFieldDescriptor;
import org.sourceid.saml20.adapter.gui.CheckBoxFieldDescriptor;
import org.sourceid.saml20.adapter.gui.LdapDatastoreFieldDescriptor;
import org.sourceid.saml20.adapter.gui.validation.impl.IntegerValidator;
import org.sourceid.saml20.adapter.gui.validation.impl.RequiredFieldValidator;
import com.pingidentity.access.DataSourceAccessor;
import org.sourceid.saml20.adapter.idp.authn.AuthnPolicy;
import org.sourceid.saml20.adapter.idp.authn.IdpAuthnAdapterDescriptor;
import org.sourceid.saml20.domain.SpConnection;
import org.sourceid.saml20.domain.mgmt.MgmtFactory;
import org.sourceid.saml20.metadata.MetaDataFactory;
import org.sourceid.saml20.metadata.partner.MetadataDirectory;

import com.pingidentity.common.security.InputValidator;
import com.pingidentity.common.security.UsernameRule;
import com.pingidentity.common.util.HTMLEncoder;
import com.pingidentity.sdk.AuthnAdapterResponse;
import com.pingidentity.sdk.IdpAuthenticationAdapterV2;

import com.toopher.api.*;

/**
 * <p>
 * This class is an example of an IdP authentication adapter that uses a
 * velocity HTML form template to display a form to the user. The username is
 * provided by a previous adapter and can not be changed. If not username
 * provided the authn will fail with an exception.
 * </p>
 */
public class ToopherSecondFactorAdapter implements IdpAuthenticationAdapterV2 {

    public static final String ADAPTER_NAME = "Toopher Multifactor Adapter";
    private final Log log = LogFactory.getLog(this.getClass());

    private static final SecureRandom secureRandom = new SecureRandom();
    private static final String TERMINAL_ID_COOKIE_NAME = "toopher_terminal_id";
    private static final int TERMINAL_ID_COOKIE_MAX_AGE = 10 * 365 * 24 * 60 * 60;  // 10 years

    public static final String FIELD_API_URL="Toopher API URL";
    public static final String DEFAULT_API_URL="https://api.toopher.com/v1/";
    public static final String DESC_API_URL = "URL For the Toopher Webservice API";

    public static final String FIELD_KEY="Toopher Key";
    public static final String DESC_KEY = "Toopher Consumer Key";

    public static final String FIELD_SECRET="Toopher Secret";
    public static final String DESC_SECRET = "Toopher Consumer Secret";

    public static String FAILED_MESSAGE = "Failed - Please try again";

    public static final String FIELD_LOGIN_TEMPLATE_NAME = "Login Template";
    public static final String DEFAULT_LOGIN_TEMPLATE_NAME = "html.form.toopher.template.html";
    public static final String DESC_LOGIN_TEMPLATE_NAME = "HTML template (in <pf_home>/server/default/conf/template) to render for login.  The default value is "
            + DEFAULT_LOGIN_TEMPLATE_NAME + ".";

    public static final String FIELD_FAILURE_TEMPLATE_NAME = "Failure Template";
    public static final String DEFAULT_FAILURE_TEMPLATE_NAME = "html.form.toopher-failure.template.html";
    public static final String DESC_FAILURE_TEMPLATE_NAME = "HTML template (in <pf_home>/server/default/conf/template) to render in the case that Toopher authentication fails.  The default value is "
            + DEFAULT_FAILURE_TEMPLATE_NAME + ".";

    public static final String FIELD_OPT_OUT = "Allow Opt-Out";
    public static final String DESC_OPT_OUT = "Whether or not users are permitted to opt-out of Toopher Authenitcation";

    public static final String FIELD_ALLOW_NON_INTERACTIVE = "Allow Non-Interactive Login";
    public static final String DESC_ALLOW_NON_INTERACTIVE = "Whether to permit non-interactive login.  If enabled, Toopher Authentication will be bypassed for non-interactive logins.";

    public static final String ATTR_NAME_AUTH_STATUS = "authentication_status";
    public static final String ATTR_NAME_USER_NAME = "username";
    public static final String ATTR_NAME_IFRAME_SRC = "iframe_src";
    public static final String ATTR_NAME_TOOPHER_ERROR = "toopher_error_message";


    // HTML form field names
    private static final String FORM_FIELD_STATE = "state";
    private static final String FORM_FIELD_ARG1 = "input1";
    private static final String FORM_FIELD_REQUEST_ID = "request_id";

    // session value keys
    private static final String REQUEST_TOKEN_SESSION_KEY = "toopherRequestToken";

    private final IdpAuthnAdapterDescriptor descriptor;
    private String htmlTemplate;
    private String htmlFailureTemplate;
    private boolean allowOptOut = false;
    private boolean allowNonInteractive = false;
    private int maxChallenges=10;
    private int numChallenges = 0;
    private ToopherIframe iframeApi;

    public ToopherSecondFactorAdapter() {

        AdapterConfigurationGuiDescriptor guiDescriptor = new AdapterConfigurationGuiDescriptor();

        TextFieldDescriptor loginTemplateName = new TextFieldDescriptor(FIELD_LOGIN_TEMPLATE_NAME, DESC_LOGIN_TEMPLATE_NAME);
        loginTemplateName.addValidator(new RequiredFieldValidator());
        loginTemplateName.setDefaultValue(DEFAULT_LOGIN_TEMPLATE_NAME);
        guiDescriptor.addField(loginTemplateName);

        TextFieldDescriptor failureTemplateName = new TextFieldDescriptor(FIELD_FAILURE_TEMPLATE_NAME, DESC_FAILURE_TEMPLATE_NAME);
        failureTemplateName.addValidator(new RequiredFieldValidator());
        failureTemplateName.setDefaultValue(DEFAULT_FAILURE_TEMPLATE_NAME);
        guiDescriptor.addField(failureTemplateName);

        TextFieldDescriptor apiUrl = new TextFieldDescriptor(FIELD_API_URL, DESC_API_URL);
        apiUrl.addValidator(new RequiredFieldValidator());
        apiUrl.setDefaultValue(DEFAULT_API_URL);
        guiDescriptor.addField(apiUrl);

        TextFieldDescriptor fieldKey = new TextFieldDescriptor(FIELD_KEY, DESC_KEY);
        fieldKey.addValidator(new RequiredFieldValidator());
        guiDescriptor.addField(fieldKey);

        TextFieldDescriptor fieldSecret= new TextFieldDescriptor(
            FIELD_SECRET, DESC_SECRET);
        fieldSecret.addValidator(new RequiredFieldValidator());
        guiDescriptor.addField(fieldSecret);

        CheckBoxFieldDescriptor fieldOptOut = new CheckBoxFieldDescriptor(FIELD_OPT_OUT, DESC_OPT_OUT);
        fieldOptOut.setDefaultValue(false);
        guiDescriptor.addField(fieldOptOut);

        CheckBoxFieldDescriptor fieldAllowNonInteractive = new CheckBoxFieldDescriptor(FIELD_ALLOW_NON_INTERACTIVE, DESC_ALLOW_NON_INTERACTIVE);
        fieldAllowNonInteractive.setDefaultValue(false);
        guiDescriptor.addField(fieldAllowNonInteractive);

        Set<String> attrNames = new HashSet<String>();
        attrNames.add(ATTR_NAME_USER_NAME);

        descriptor = new IdpAuthnAdapterDescriptor(this, ADAPTER_NAME,
                attrNames, false, guiDescriptor, false);

    }

    private void debug_message(String message) {
        log.debug(message);
        System.out.println("**********************************");
        System.out.println(message);
    }

    public IdpAuthnAdapterDescriptor getAdapterDescriptor() {

        return descriptor;
    }

    @SuppressWarnings("rawtypes")
    public boolean logoutAuthN(Map authnIdentifiers, HttpServletRequest req,
                               HttpServletResponse resp, String resumePath)
    throws AuthnAdapterException, IOException {

        return true;
    }

    public void configure(Configuration configuration) {

        debug_message("ToopherSecondFactorAdapter::configure");

        htmlTemplate = configuration.getFieldValue(FIELD_LOGIN_TEMPLATE_NAME);
        htmlFailureTemplate = configuration.getFieldValue(FIELD_FAILURE_TEMPLATE_NAME);
        String toopherApiUrl = configuration.getFieldValue(FIELD_API_URL);
        String toopherKey = configuration.getFieldValue(FIELD_KEY);;
        String toopherSecret = configuration.getFieldValue(FIELD_SECRET);
        allowOptOut = configuration.getBooleanFieldValue(FIELD_OPT_OUT);
        allowNonInteractive = configuration.getBooleanFieldValue(FIELD_ALLOW_NON_INTERACTIVE);

        iframeApi = new ToopherIframe(toopherKey, toopherSecret, toopherApiUrl);
    }

    public Map<String, Object> getAdapterInfo() {

        return null;
    }

    private static String setRequestToken(HttpServletRequest req) {
        String requestToken = new BigInteger(20 * 8, secureRandom).toString(32);
        req.getSession().setAttribute(REQUEST_TOKEN_SESSION_KEY, requestToken);
        return requestToken;
    }

    @SuppressWarnings( { "rawtypes", "unchecked" })
    public AuthnAdapterResponse lookupAuthN(HttpServletRequest req,
                                            HttpServletResponse resp, Map<String, Object> inParameters)
    throws AuthnAdapterException, IOException {

        debug_message("ToopherSecondFactorAdapter: lookupAuthN");
        AuthnAdapterResponse authnAdapterResponse = new AuthnAdapterResponse();
        authnAdapterResponse.setAuthnStatus(AuthnAdapterResponse.AUTHN_STATUS.IN_PROGRESS);

        HashMap<String, Object> adapterAttributes = new HashMap<String, Object>();

        debug_message("inParameters:");
        for (Map.Entry<String, Object> e : inParameters.entrySet()) {
            debug_message("  " + e.getKey() + " : " + e.getValue().toString());
        }

        debug_message("request Parameters:");
        for (Map.Entry<String, String[]> reqParam : req.getParameterMap().entrySet()) {
            debug_message("  " + reqParam.getKey() + " : " + reqParam.getValue()[0].toString());
        }

        // make sure we're in an interactive session
        AuthnPolicy authnPolicy = (AuthnPolicy) inParameters.get(IN_PARAMETER_NAME_AUTHN_POLICY);
        if (!authnPolicy.allowUserInteraction()) {
            // not interactive - Toopher cannot authenticate
            if(allowNonInteractive) {
                authnAdapterResponse.setAuthnStatus(AuthnAdapterResponse.AUTHN_STATUS.SUCCESS);
            } else {
                authnAdapterResponse.setAuthnStatus(AuthnAdapterResponse.AUTHN_STATUS.FAILURE);
            }
            return authnAdapterResponse;
        }

        String resumePath = inParameters.get(IN_PARAMETER_NAME_RESUME_PATH).toString();
        String partnerEntityId = inParameters.get(IN_PARAMETER_NAME_PARTNER_ENTITYID).toString();
        String userName = inParameters.get(IN_PARAMETER_NAME_USERID).toString();

        String responseTemplate = htmlTemplate;
        Map<String, Object> responseParams = new HashMap<String, Object>();
        responseParams.put("url", resumePath);

        String requestToken = (String)req.getSession().getAttribute(REQUEST_TOKEN_SESSION_KEY);
        if (requestToken != null && req.getParameterMap().containsKey("toopher_sig")) {
            // validate postback
            debug_message("Session requestToken = " + requestToken);
            req.getSession().removeAttribute(REQUEST_TOKEN_SESSION_KEY);

            try {
                Map<String, String> validatedData = iframeApi.validate(req.getParameterMap(), requestToken);

                if (validatedData.containsKey("error_code")) {
                    // check for API errors
                    String errorCode = validatedData.get("error_code");
                    if (errorCode.equals(ToopherIframe.PAIRING_DEACTIVATED)) {
                        // User deleted the pairing on their mobile device.
                        //
                        // Your server should display a Toopher Pairing iframe so their account can be re-paired
                        //
                        responseParams.put(ATTR_NAME_IFRAME_SRC, iframeApi.pairUri(userName, null));
                    } else if (errorCode.equals(ToopherIframe.USER_OPT_OUT)) {
                        // User has been marked as "Opt-Out" in the Toopher API
                        //
                        // If your service allows opt-out, the user should be granted access.
                        //
                        if(allowOptOut) {
                            responseTemplate = null;
                            authnAdapterResponse.setAuthnStatus(AuthnAdapterResponse.AUTHN_STATUS.SUCCESS);
                        } else {
                            String message = "This service is not configured to allow users to Opt-Out of Multi-Factor Authentication";
                            log.warn(message);
                            responseParams.put(ATTR_NAME_TOOPHER_ERROR, message);
                            responseTemplate = htmlFailureTemplate;
                            authnAdapterResponse.setAuthnStatus(AuthnAdapterResponse.AUTHN_STATUS.FAILURE);
                        }
                    } else if (errorCode.equals(ToopherIframe.USER_UNKNOWN)) {
                        // User has never authenticated with Toopher on this server
                        //
                        // Your server should display a Toopher Pairing iframe so their account can be paired
                        responseParams.put(ATTR_NAME_IFRAME_SRC, iframeApi.pairUri(userName, null));
                    } else {
                        String message = "Unknown toopher error returned: " + errorCode;
                        log.warn(message);
                        responseParams.put(ATTR_NAME_TOOPHER_ERROR, message);
                        responseTemplate = htmlFailureTemplate;
                        authnAdapterResponse.setAuthnStatus(AuthnAdapterResponse.AUTHN_STATUS.FAILURE);
                    }
                } else {
                    // signature is valid, and no api errors.  check authentication result
                    boolean authPending = validatedData.get("pending").toLowerCase().equals("true");
                    boolean authGranted = validatedData.get("granted").toLowerCase().equals("true");

                    // authenticationResult is the ultimate result of Toopher second-factor authentication
                    if(authGranted && !authPending) {
                        responseTemplate = null;
                        authnAdapterResponse.setAuthnStatus(AuthnAdapterResponse.AUTHN_STATUS.SUCCESS);
                    } else {
                        responseParams.put(ATTR_NAME_TOOPHER_ERROR, "Toopher Authentication has denied the request");
                        responseTemplate = htmlFailureTemplate;
                        authnAdapterResponse.setAuthnStatus(AuthnAdapterResponse.AUTHN_STATUS.FAILURE);
                    }
                }

            } catch (ToopherIframe.SignatureValidationError e) {
                // signature was invalid.  User should not authenticated
                //
                // e.getMessage() will return more information about what specifically
                // went wrong (incorrect session token, expired TTL, invalid signature)
                log.warn("Invalid ToopherIframe signature", e);
                responseParams.put(ATTR_NAME_TOOPHER_ERROR, e.getMessage());
                responseTemplate = htmlFailureTemplate;
                authnAdapterResponse.setAuthnStatus(AuthnAdapterResponse.AUTHN_STATUS.FAILURE);
            }

        } else {
            // first call - serve up a signed iframe link
            responseParams.put(ATTR_NAME_IFRAME_SRC, iframeApi.loginUri(userName, null, setRequestToken(req)));
        }

        if (responseTemplate != null) {
            ResponseTemplateRenderer renderer = ResponseTemplateRenderer.getInstance();
            renderer.render(req, resp, responseTemplate, responseParams);
        }

        adapterAttributes.put(ATTR_NAME_USER_NAME, userName);
        authnAdapterResponse.setAttributeMap(adapterAttributes);
        return authnAdapterResponse;


    }

    /**
     * This method is deprecated. It is not called when
     * IdpAuthenticationAdapterV2 is implemented. It is replaced by
     * {@link #lookupAuthN(HttpServletRequest, HttpServletResponse, Map)}
     *
     * @deprecated
     */
    @SuppressWarnings(value = { "rawtypes" })
    public Map lookupAuthN(HttpServletRequest req, HttpServletResponse resp,
                           String partnerSpEntityId, AuthnPolicy authnPolicy, String resumePath)
    throws AuthnAdapterException, IOException {

        throw new UnsupportedOperationException();
    }

}
