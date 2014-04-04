# Toopher IDP adapter for PingFederate

This is the Toopher integration for
[PingFederate](https://www.pingidentity.com/products/pingfederate/).
Using this integration you can easily add
[Toopher two-factor authentication](https://www.toopher.com/) to your
PingFederate server.

## Prerequisites
PingFederate version 7.1 or compatible.

## Build Instructions
The PingFederate SDK must be available to build this plugin.

The following instructions are adapted from the PingFederate [Getting Started With the SDK](http://documentation.pingidentity.com/display/PF71/Getting+Started+With+the+SDK) page.  In the command-line examples, `${PINGFEDERATE}` should point to the root of your PingFederate server installation, and `${TOOPHER_SOURCE}` should point to the contents of this archive (i.e. the directory that holds this README file).

Link this directory as `idp-toopher` under the sdk plugin-src directory:

    ln -s ${TOOPHER_SOURCE}/plugin-src/idp-toopher ${PINGFEDERATE}/sdk/plugin-src/idp-toopher

Edit `${PINGFEDERATE}/sdk/build.local.properties` to build the idp-toopher plugin:

    target-plugin.name=idp-toopher

Change to the PingFederate SDK directory, and run `ant` to build and install the idp_toopher jar files:

    ant deploy-plugin

Finally, copy the template files, and ensure they are readable by the account that runs the PingFederate server (`pingfederate` in the example below):

    sudo cp -r ${TOOPHER_SOURCE}/plugin-src/idp-toopher/server/default/conf/template/* ${PINGFEDERATE}/server/default/conf/template/
    sudo chown -R pingfederate:pingfederate ${PINGFEDERATE}/server/default/conf/template

Note: if you are building for a PingFederate cluster, you will need to
deploy the plugin on each instance.

## Configuring the PingFederate IDP Adapter
Once the plugin is installed, open the PingFederate Administrative interface in a web browser to configure Toopher IDP to work alongside your primary username/password IDP adapter.  This guide assumes that the primary IDP adapter is already configured and named `UsernamePasswordIdp`.

### Instantiate the Toopher IDP adapter
* In the "Application Integration Settings" section of the PingFederate Administrative interface, click `Adapters` to open the "Manage IDP Adapter Instances" view.
* Click "Create New Instance", and set the adapter type to `Toopher Multifactor Adapter`.  Enter values for `Instance Name` and `Instance Id` - this guide will simply use `Toopher` for both.  Leave Parent Instance set to `None`, and click "Next".
* Enter your Toopher API consumer key and secret in the appropriate fields, then click "Next".  If you do not already have an API key and secret, you can provision new credentials on the [Toopher Developer Site](https://dev.toopher.com).
* In the "Adapter Attributes" view, enable the `Pseudonym` checkbox for the `username` attribute, and click "Next".  On the "Summary" page, click "Done" to return to the "Manage IDP Adapter Instances" page.
* Click "Save" to persist the changes.

### Create a Composite IDP adapter
* Again from the "Manage IDP Adapter Instances" page, click "Create New Instance".
* Set adapter type to `Composite Adapter`, and assign a name and id.  This guide will use `ToopherCompositeAdapter` for both name and id.  Click "Next".
* In the "Adapters" section, click "Add a new row to 'Adapters'".  When prompted, set `Adapter Instance` = `UsernamePasswordIdp`, `Policy` = `Required`.  Click "Update" to insert the row.
* Click "Add a new row to 'Adapters'" again.  For Adapter Instance, select the Toopher IDP adapter created in the previous step, set `Policy` = `Required`.  Click "Update".
* In the "Input User ID Mapping" section, click "Add a new row".  Set `Target Adapter` to the Toopher IDP adapter, and `User ID Selection` to `username`.  Click "Update", then click "Next".
* In the "Extended Contract" page, enter `username`, and click "Add".  Click "Next".
* In the "Adapter Attributes" page, enable `Pseudonym` for the `username` attribute.  Click "Next".
* In the "Summary" page, click "Done", then click "Save" to persist the changes.

### Configure the SSO Service Provider connection
Once the Toopher Composite IDP adapter is created, Browser SSO Service Provider (SP) Connections can be individually configured to use the new IDP.  Currently, the Toopher IDP is only validated for use with Browser SSO SP Conections.

---

## FAQ

### How can users un-pair their mobile device from Toopher?
Users can delete the pairing from their mobile device by tapping on the pairing in the Toopher mobile app, then selecting "Remove Pairing".  The next time they authenticate, the user will be prompted to re-pair their account with a mobile device.

### Can users authenticate if their mobile device is not connected to the network?
Yes, users can still authenticate with a One-Time Password by clicking on the "Authenticate with
One-Time Password" button when logging in.  The Toopher mobile app can generate valid One-Time
Passwords regardless of network connectivity.

### What happens if users lose their mobile device, or delete the Toopher app?
Currently, this situation requires an administrator to manually reset the user's Toopher Pairing
status by running `reset_user.py` script, available in the `tools` directory of the installation archive.
`reset_user.py` requires access to the same Toopher Consumer Key and Secret used to configure the Toopher
OpenAM module.  There are two ways to supply these credentials to the script:

1. Set the `TOOPHER_CONSUMER_KEY` and `TOOPHER_CONSUMER_SECRET` environment variables *(preferred)*
1. Manually enter them when running the script - `reset_user.py` will prompt for the key and secret
if they are not available in environment variables

Whichever method is chosen for setting the Toopher API Credentials, `reset_user.py` takes a single
command-line argument: the UID for the OpenAM user needing to be reset.

    # reset the Toopher status of a user with uid `johndoe`.
    # Assumes that TOOPHER_CONSUMER_KEY and TOOPHER_CONSUMER_SECRET environment variables are set

    python tools/reset_user.py johndoe

---

## Changelog

v1.0

* initial release
