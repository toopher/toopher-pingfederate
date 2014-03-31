import sys
import os
import toopher
import urllib

DEFAULT_TOOPHER_KEY = ''          # Toopher Requester Credentials can be entered in these constants.
DEFAULT_TOOPHER_SECRET = ''

class AdvancedToopherApi(toopher.ToopherApi):
    def find_users_by_name(self, user_name):
        uri = self.base_url + '/users?' + urllib.urlencode(dict(name=user_name))
        print 'making request to ' + uri
        return self._request(uri, "GET")

    def get_user_pairings(self, user):
        uri = self.base_url + '/users/{0}/pairings'.format(user["id"])
        return self._request(uri, "GET")

    def set_toopher_deactivated_for_user(self, user, deactivated):
        uri = self.base_url + '/users/{0}'.format(user['id'])
        params = dict(disable_toopher_auth=deactivated)
        return self._request(uri, "POST", params)

    def deactivate_pairing(self, pairing):
        uri = self.base_url + '/pairings/{0}'.format(pairing['id'])
        params = dict(deactivated=True)
        return self._request(uri, 'POST', params)

if __name__ == '__main__':
    if len(sys.argv) != 2:
        print 'Usage: {0} [username]'.format(sys.argv[0])
        exit(-1)
    
    user_name = sys.argv[1]

    key = os.environ.get('TOOPHER_CONSUMER_KEY')
    secret = os.environ.get('TOOPHER_CONSUMER_SECRET')

    if not key:
        key = DEFAULT_TOOPHER_KEY
    if not secret:
        secret = DEFAULT_TOOPHER_SECRET
    
    if not (key or secret):
        print 'Setup Credentials (set environment variables to prevent prompting)'
        print '-'*72
        print 'Enter your requester credential details (from https://dev.toopher.com)'
        while not key:
            key = raw_input('TOOPHER_CONSUMER_KEY=')
        while not secret:
            secret = raw_input('TOOPHER_CONSUMER_SECRET=')
            
    api = AdvancedToopherApi(key, secret, os.environ.get('TOOPHER_BASE_URL'))
    users = api.find_users_by_name(user_name)
    if not users:
        print 'Error: no users returned for the specified requester credentials'
        exit(-1)

    for user in users:
        print '  for user with id={0}'.format(user['id'])
        api.set_toopher_deactivated_for_user(user, False)
        pairings = api.get_user_pairings(user)
        for pairing in pairings:
            print '    deactivating pairing with id={0}'.format(pairing['id'])
            api.deactivate_pairing(pairing)

    print '\ndone.'
