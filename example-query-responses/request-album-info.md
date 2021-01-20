Query:

Request URL: 

http://127.0.0.1:32400/library/metadata/18396?includeExternalMedia=1&Accept-Language=en&includeConcerts=1&includeExtras=1&includeOnDeck=1&includePopularLeaves=1&includePreferences=1&includeChapters=1&includeStations=1&includeExternalMedia=1&asyncAugmentMetadata=1&asyncCheckFiles=1&asyncRefreshAnalysis=1&asyncRefreshLocalMediaAgent=1&X-Plex-Product=Plex%20Web&X-Plex-Version=4.22.2&X-Plex-Client-Identifier=REDACTED&X-Plex-Platform=Firefox&X-Plex-Platform-Version=76.0&X-Plex-Sync-Version=2&X-Plex-Features=external-media%2Cindirect-media&X-Plex-Model=bundled&X-Plex-Device=Linux&X-Plex-Device-Name=Firefox&X-Plex-Device-Screen-Resolution=1600x805%2C1600x900&X-Plex-Token=REDACTED&X-Plex-Language=en&X-Plex-Text-Format=plain&X-Plex-Provider-Version=1.3&X-Plex-Drm=widevine

Request headers:

Host: 127.0.0.1:32400
User-Agent: Mozilla/5.0 (X11; Ubuntu; Linux x86_64; rv:76.0) Gecko/20100101 Firefox/76.0
Accept: application/json
Accept-Language: en-US,en;q=0.5
Accept-Encoding: gzip, deflate
Referer: http://localhost:32400/web/index.html
DNT: 1
Connection: keep-alive
Upgrade-Insecure-Requests: 1
Cache-Control: max-age=0, no-cache
Pragma: no-cache

Response:


{
  "MediaContainer": {
    "size": 1,
    "allowSync": true,
    "augmentationKey": "/library/metadata/augmentations/4",
    "identifier": "com.plexapp.plugins.library",
    "librarySectionID": 6,
    "librarySectionTitle": "Test Audiobooks",
    "librarySectionUUID": "2ac71176-811e-443b-9e02-d92c98adc7d8",
    "mediaTagPrefix": "/system/bundle/media/flags/",
    "mediaTagVersion": 1582114654,
    "Metadata": [
      {
        "ratingKey": "15982",
        "key": "/library/metadata/15982/children",
        "parentRatingKey": "15984",
        "guid": "local://15982",
        "parentGuid": "com.plexapp.agents.none://15984?lang=en",
        "type": "album",
        "title": "The Cosmic Computer",
        "parentKey": "/library/metadata/15984",
        "librarySectionTitle": "Test Audiobooks",
        "librarySectionID": 6,
        "librarySectionKey": "/library/sections/6",
        "parentTitle": "H. Beam Piper",
        "summary": "",
        "index": 1,
        "viewCount": 2,
        "lastViewedAt": 1591031072,
        "thumb": "/library/metadata/15982/thumb/1589583717",
        "leafCount": 1,
        "viewedLeafCount": 1,
        "addedAt": 1589583683,
        "updatedAt": 1589583717,
        "loudnessAnalysisVersion": "1",
        "Field": [
          {
            "locked": true,
            "name": "title"
          },
          {
            "locked": true,
            "name": "titleSort"
          },
          {
            "locked": true,
            "name": "thumb"
          }
        ],
        "Extras": {
          "size": 0
        }
      }
    ]
  }
}

