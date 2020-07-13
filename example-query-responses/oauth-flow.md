Query 1:

https://plex.tv/api/v2/pins.json?strong=true

headers:
X-Plex-Product: Name
X-Plex-Platform: Web
X-Plex-Device: Name (Web)
X-Plex-Client-Identifier: CLIENT_RED (this should be unique to the client instance)

OAuth responses:
{
    "id": #########,
    "code": "CODE_BLUE", // return by plex, will look like a string of gibberish
    "trusted": false,
    "clientIdentifier": "CLIENT_RED",
    "location": {
        "code": "US",
        "country": "United States",
        "city": "City",
        "time_zone": "America/New_York",
        "postal_code": "zip_code",
        "subdivisions": "state",
        "coordinates": "lat, long"
    },
    "expiresIn": 1799,
    "createdAt": "2019-03-28T00:56:17.247Z",
    "expiresAt": "2019-03-28T01:26:17.245Z",
    "authToken": null
}


Query 2:


https://app.plex.tv/auth#?code=CODE_BLUE
&context[device][product]=APP_NAME   
&context[device][environment]=bundled
&context[device][layout]=desktop    // or your platform
&context[device][platform]=Web      // or your platform
&context[device][device]=APP_NAME
&clientID=CLIENT_RED
&forwardUrl=https://callback.domain // don't include this unless you're on desktop, it redirects browser containing plex's oauth login after login completes

Response:


{
    "id": #########,
    "code": "CODE_BLUE", // return by plex, will look like a string of gibberish
    "trusted": false,
    "clientIdentifier": "CLIENT_RED",
    "location": {
        "code": "US",
        "country": "United States",
        "city": "City",
        "time_zone": "America/New_York",
        "postal_code": "zip_code",
        "subdivisions": "state",
        "coordinates": "lat, long"
    },
    "expiresIn": 1799,
    "createdAt": "2019-03-28T00:56:17.247Z",
    "expiresAt": "2019-03-28T01:26:17.245Z",
    "authToken": "SOME_AUTH_CODE"    // <-- this will now be provided by plex if auth succeeds!
}

Include X-PLEX-TOKEN=SOME_AUTH_CODE in future queries which require auth
