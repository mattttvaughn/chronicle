This can be used for a lot of things, but accessing shared libraries is why I'm adding this, as
plex doesn't give shared users access to /library/sections

Query:

https://10-0-0-32.a167b87e34cb42d7b361bb4514e3f282.plex.direct:32400/media/providers?X-Plex-Product=Plex%20Web&X-Plex-Version=4.34.2&X-Plex-Client-Identifier=REDACTED&X-Plex-Platform=Firefox&X-Plex-Platform-Version=76.0&X-Plex-Sync-Version=2&X-Plex-Features=external-media%2Cindirect-media&X-Plex-Model=hosted&X-Plex-Device=Linux&X-Plex-Device-Name=Firefox&X-Plex-Device-Screen-Resolution=1600x459%2C1600x900&X-Plex-Token=REDACTED&X-Plex-Language=en

Response:

{
  "MediaContainer": {
    "size": 1,
    "allowCameraUpload": false,
    "allowChannelAccess": false,
    "allowSharing": true,
    "allowSync": true,
    "allowTuners": false,
    "backgroundProcessing": true,
    "certificate": true,
    "companionProxy": true,
    "countryCode": "usa",
    "diagnostics": "logs,databases,streaminglogs",
    "eventStream": true,
    "friendlyName": "XPS",
    "livetv": 7,
    "machineIdentifier": "[REMOVED FOR MY PRIVACY]",
    "myPlex": true,
    "myPlexMappingState": "mapped",
    "myPlexSigninState": "ok",
    "myPlexSubscription": true,
    "myPlexUsername": "mattmabinogi@gmail.com",
    "ownerFeatures": "[REMOVED DUE TO LENGTH]",
    "photoAutoTag": true,
    "platform": "Linux",
    "platformVersion": "[Removed so you don't get this]",
    "pluginHost": true,
    "pushNotifications": false,
    "readOnlyLibraries": true,
    "streamingBrainABRVersion": 3,
    "streamingBrainVersion": 2,
    "sync": true,
    "transcoderActiveVideoSessions": 0,
    "transcoderAudio": true,
    "transcoderLyrics": true,
    "transcoderSubtitles": true,
    "transcoderVideo": true,
    "transcoderVideoBitrates": "64,96,208,320,720,1500,2000,3000,4000,8000,10000,12000,20000",
    "transcoderVideoQualities": "0,1,2,3,4,5,6,7,8,9,10,11,12",
    "transcoderVideoResolutions": "128,128,160,240,320,480,768,720,720,1080,1080,1080,1080",
    "updatedAt": 1590165503,
    "updater": true,
    "version": "1.18.7.2438-f342a5a43",
    "voiceSearch": true,
    "MediaProvider": [
      {
        "identifier": "com.plexapp.plugins.library",
        "title": "Library",
        "types": "video,audio,photo",
        "protocols": "stream",
        "Feature": [
          {
            "key": "/library/sections",
            "type": "content",
            "Directory": [
              {
                "hubKey": "/hubs",
                "title": "Home"
              },
              {
                // \/ \/ \/   THE JUICY BITS   \/ \/ \/
                "agent": "com.plexapp.agents.none",
                "language": "en",
                "refreshing": false,
                "scanner": "Plex Music Scanner",
                "uuid": "2ac71176-811e-443b-9e02-d92c98adc7d8",
                "id": "6",
                "key": "/library/sections/6",
                "hubKey": "/hubs/sections/6",
                "type": "artist",
                "title": "Test Audiobooks",
                "updatedAt": 1582417667,
                "scannedAt": 1590163561,
                "Pivot": [
                  {
                    "key": "/hubs/sections/6",
                    "type": "hub",
                    "title": "Recommended",
                    "context": "content.discover",
                    "symbol": "star"
                  },
                  {
                    "key": "/library/sections/6/all?type=8",
                    "type": "list",
                    "title": "Library",
                    "context": "content.library",
                    "symbol": "library"
                  }
                ]
              }
            ]
          },
          {
            "key": "/hubs/search",
            "type": "search"
          },
          {
            "key": "/library/matches",
            "type": "match"
          },
          {
            "key": "/library/metadata",
            "type": "metadata"
          },
          {
            "key": "/:/rate",
            "type": "rate"
          },
          {
            "key": "/photo/:/transcode",
            "type": "imagetranscoder"
          },
          {
            "key": "/hubs?promoted=1&includeTypeFirst=1",
            "type": "promoted"
          },
          {
            "flavor": "universal",
            "key": "/playlists",
            "type": "playlist"
          },
          {
            "flavor": "universal",
            "key": "/playQueues",
            "type": "playqueue"
          },
          {
            "scrobbleKey": "/:/scrobble",
            "unscrobbleKey": "/:/unscrobble",
            "key": "/:/timeline",
            "type": "timeline"
          },
          {
            "type": "queryParser"
          },
          {
            "flavor": "download",
            "type": "subscribe"
          }
        ]
      }
    ]
  }
}