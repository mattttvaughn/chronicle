# Request users:

## Request users from Plex.tv:

### Example query:
    
`https://plex.tv/api/v2/home/users?X-Plex-Product=Plex%20Web&X-Plex-Version=4.22.2&X-Plex-Client-Identifier=0quy6cfpwxwkl9wemdv394t7&X-Plex-Platform=Firefox&X-Plex-Platform-Version=77.0&X-Plex-Sync-Version=2&X-Plex-Features=external-media%2Cindirect-media&X-Plex-Model=bundled&X-Plex-Device=Linux&X-Plex-Device-Name=Firefox&X-Plex-Device-Screen-Resolution=1600x459%2C1600x900&X-Plex-Token=A4qo2oEM5ikby1sLpkDC&X-Plex-Language=en`

### Headers:z
    
```
HTTP/1.1 200 OK
Date: Mon, 15 Jun 2020 15:42:06 GMT
Content-Type: application/json
Transfer-Encoding: chunked
Connection: keep-alive
Access-Control-Allow-Origin: *
Access-Control-Allow-Methods: GET, POST, PUT, PATCH, HEAD, DELETE, OPTIONS
Access-Control-Expose-Headers: Location
Access-Control-Max-Age: 86400
ETag: W/"c66d1e27cd42ddc26fbeb1ca8ab1e270"
Cache-Control: max-age=0, private, must-revalidate
Set-Cookie: _my-plex_session_32=OVh4ZDJtbWhYVTVFMm5kNlZ0NnpoOG1WU0hEdFNiTnBjSy94WUdOb0tzVlNONHo5R09wTVhyRzJGb242bzFjSFBVcGJrNE8yOHMrTHFmNEcyUmZDU3c9PS0tK1VlOExvQTdKYU1MY0QyZVBKdHFJZz09--5c1d423602c81f6221741d6b07ad764799862f6a; path=/; HttpOnly; secure
X-Request-Id: e33a60ec-d47a-4556-b1f9-b8ff2bc06476
X-Runtime: 0.033216
Strict-Transport-Security: max-age=0
Referrer-Policy: origin-when-cross-origin
X-Content-Type-Options: nosniff
X-Frame-Options: DENY
X-XSS-Protection: 1; mode=block
Vary: Origin
Content-Encoding: gzip
```

### Response

```
{
  "id": REDACTED,
  "name": "REDACTED",
  "guestUserID": 33542083,
  "guestUserUUID": "f7a3c8f863873771",
  "guestEnabled": false,
  "subscription": true,
  "users": [
    {
      "id": 2737029,
      "uuid": "ADMIN_UUID",
      "title": "REDACTED",
      "username": "REDACTED",
      "email": "REDACTED",
      "thumb": "https://plex.tv/users/REDACTED/avatar?c=1592234494",
      "hasPassword": true, // PIN REQUIRED IF TRUE
      "restricted": false, // IRRELEVANT FOR AUDIO LIBRARIES
      "restrictionProfile": null,
      "admin": true,
      "guest": false,
      "protected": true   // PIN REQUIRED IF TRUE???
    },
    {
      "id": 33542084,
      "uuid": "USER_UUID",
      "title": "this is a user",
      "username": null,
      "email": null,
      "thumb": "https://plex.tv/users/19860c4bdfbf24d5/avatar?c=1592234475",
      "hasPassword": false,
      "restricted": true,
      "restrictionProfile": "little_kid",
      "admin": false,
      "guest": false,
      "protected": false
    }
  ]
}
```


## Attempt to choose a user:

### Example query:

```
https://plex.tv/api/v2/home/users/USER_UUID/switch?&includeProviders=0&includeSettings=0&includeSharedSettings=0&pin=8989&X-Plex-Product=Plex%20Web&X-Plex-Version=4.22.2&X-Plex-Client-Identifier=REDACTED&X-Plex-Platform=Firefox&X-Plex-Platform-Version=77.0&X-Plex-Sync-Version=2&X-Plex-Features=external-media%2Cindirect-media&X-Plex-Model=bundled&X-Plex-Device=Linux&X-Plex-Device-Name=Firefox&X-Plex-Device-Screen-Resolution=1600x459%2C1600x900&X-Plex-Token=REDACTED&X-Plex-Language=en
```

^^^ Note `pin=8989`, this is the pin entered by the user

### Query headers

    ```
    Host: plex.tv
    User-Agent: Mozilla/5.0 (X11; Ubuntu; Linux x86_64; rv:77.0) Gecko/20100101 Firefox/77.0
    Accept: application/json
    Accept-Language: en
    Accept-Encoding: gzip, deflate, br
    Origin: http://localhost:32400
    DNT: 1
    Connection: keep-alive
    Referer: http://localhost:32400/web/index.html
    Content-Length: 0
    ```


### Response for success (correct pin or no pin needed):


```
{
  "id": 2737029,
  "uuid": "d320a7e6324aacd5",
  "username": "REDACTED",
  "title": "REDACTED",
  "email": "REDACTED",
  "emailOnlyAuth": false,
  "hasPassword": true,
  "protected": true,
  "thumb": "https://plex.tv/users/REDACTED/avatar?c=1592234494",
  "authToken": "REDACTED",
  "providers": [],
  "pin": "REDACTED",
  "restricted": false,
  "anonymous": null,
  "home": true,
  "guest": false,
  "homeSize": 2,
  "homeAdmin": true,
  "maxHomeSize": 15,
  "certificateVersion": 2,
  "rememberExpiresAt": 1593445786,
}
```


### Response for failure:

```
{
  "errors": [
    {
      "code": 1041,
      "message": "A valid PIN is required to perform this action"
    }
  ]
}
```
