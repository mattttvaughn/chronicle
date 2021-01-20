Query:

Request URL: http://127.0.0.1:32400/library/metadata/15775?checkFiles=1&includeAllConcerts=1&includeBandwidths=1&includeChapters=1&includeChildren=1&includeConcerts=1&includeExtras=1&includeFields=1&includeGeolocation=1&includeLoudnessRamps=1&includeOnDeck=1&includePopularLeaves=1&includePreferences=1&includeRelated=1&includeRelatedCount=1&includeReviews=1&includeStations=1&X-Plex-Token=REDACTED

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
    "identifier": "com.plexapp.plugins.library",
    "librarySectionID": 5,
    "librarySectionTitle": "Audiobooks (Public Domain)",
    "librarySectionUUID": "62ec86d8-eb45-4bd6-b9b7-6705136039b4",
    "mediaTagPrefix": "/system/bundle/media/flags/",
    "mediaTagVersion": 1582114654,
    "Metadata": [
      {
        "ratingKey": "15775",
        "key": "/library/metadata/15775",
        "parentRatingKey": "15774",
        "grandparentRatingKey": "15768",
        "guid": "local://15775",
        "parentGuid": "com.plexapp.agents.none://15774?lang=xn",
        "grandparentGuid": "com.plexapp.agents.none://15768?lang=xn",
        "type": "track",
        "title": "War of the Worlds",
        "grandparentKey": "/library/metadata/15768",
        "parentKey": "/library/metadata/15774",
        "librarySectionTitle": "Audiobooks (Public Domain)",
        "librarySectionID": 5,
        "librarySectionKey": "/library/sections/5",
        "grandparentTitle": "H. G. Wells",
        "parentTitle": "The War of the Worlds",
        "originalTitle": "H. G. Wells",
        "summary": "",
        "thumb": "/library/metadata/15774/thumb/1582417777",
        "parentThumb": "/library/metadata/15774/thumb/1582417777",
        "duration": 24214698,
        "addedAt": 1579279352,
        "updatedAt": 1582417777,
        "chapterSource": "media",
        "Media": [
          {
            "id": 17254,
            "duration": 24214698,
            "bitrate": 65,
            "audioChannels": 1,
            "audioCodec": "aac",
            "container": "mp4",
            "optimizedForStreaming": 1,
            "audioProfile": "lc",
            "has64bitOffsets": false,
            "Part": [
              {
                "id": 17263,
                "key": "/library/parts/17263/1579279352/file.m4b",
                "duration": 24214698,
                "file": "/disks/elements/Audiobooks (Public Domain)/War of the Worlds/WarOfTheWorlds_librivox.m4b",
                "size": 196328843,
                "audioProfile": "lc",
                "container": "mp4",
                "deepAnalysisVersion": "4",
                "has64bitOffsets": false,
                "optimizedForStreaming": true,
                "requiredBandwidths": "64,64,64,64,64,64,64,64"
              }
            ]
          }
        ],
        "Chapter": [
          {
            "id": 22999,
            "filter": "thumb=22999",
            "tag": "The War of the Worlds - Book 1 - Chapter 01",
            "index": 1,
            "startTimeOffset": 0,
            "endTimeOffset": 832003
          },
          {
            "id": 23000,
            "filter": "thumb=23000",
            "tag": "The War of the Worlds - Book 1 - Chapter 02",
            "index": 2,
            "startTimeOffset": 832003,
            "endTimeOffset": 1333008
          },
          {
            "id": 23001,
            "filter": "thumb=23001",
            "tag": "The War of the Worlds - Book 1 - Chapter 03",
            "index": 3,
            "startTimeOffset": 1333008,
            "endTimeOffset": 1744014
          },
          {
            "id": 23002,
            "filter": "thumb=23002",
            "tag": "The War of the Worlds - Book 1 - Chapter 04",
            "index": 4,
            "startTimeOffset": 1744014,
            "endTimeOffset": 2124007
          },
          {
            "id": 23003,
            "filter": "thumb=23003",
            "tag": "The War of the Worlds - Book 1 - Chapter 05",
            "index": 5,
            "startTimeOffset": 2124007,
            "endTimeOffset": 2746012
          },
          {
            "id": 23004,
            "filter": "thumb=23004",
            "tag": "The War of the Worlds - Book 1 - Chapter 06",
            "index": 6,
            "startTimeOffset": 2746012,
            "endTimeOffset": 3143003
          },
          {
            "id": 23005,
            "filter": "thumb=23005",
            "tag": "The War of the Worlds - Book 1 - Chapter 07",
            "index": 7,
            "startTimeOffset": 3143003,
            "endTimeOffset": 3752016
          },
          {
            "id": 23006,
            "filter": "thumb=23006",
            "tag": "The War of the Worlds - Book 1 - Chapter 08",
            "index": 8,
            "startTimeOffset": 3752016,
            "endTimeOffset": 4173008
          },
          {
            "id": 23007,
            "filter": "thumb=23007",
            "tag": "The War of the Worlds - Book 1 - Chapter 09",
            "index": 9,
            "startTimeOffset": 4173008,
            "endTimeOffset": 4980005
          },
          {
            "id": 23008,
            "filter": "thumb=23008",
            "tag": "The War of the Worlds - Book 1 - Chapter 10",
            "index": 10,
            "startTimeOffset": 4980005,
            "endTimeOffset": 5806010
          },
          {
            "id": 23009,
            "filter": "thumb=23009",
            "tag": "The War of the Worlds - Book 1 - Chapter 11",
            "index": 11,
            "startTimeOffset": 5806010,
            "endTimeOffset": 6841020
          },
          {
            "id": 23010,
            "filter": "thumb=23010",
            "tag": "The War of the Worlds - Book 1 - Chapter 12",
            "index": 12,
            "startTimeOffset": 6841020,
            "endTimeOffset": 8262020
          },
          {
            "id": 23011,
            "filter": "thumb=23011",
            "tag": "The War of the Worlds - Book 1 - Chapter 13",
            "index": 13,
            "startTimeOffset": 8262020,
            "endTimeOffset": 8913017
          },
          {
            "id": 23012,
            "filter": "thumb=23012",
            "tag": "The War of the Worlds - Book 1 - Chapter 14",
            "index": 14,
            "startTimeOffset": 8913017,
            "endTimeOffset": 10349014
          },
          {
            "id": 23013,
            "filter": "thumb=23013",
            "tag": "The War of the Worlds - Book 1 - Chapter 15",
            "index": 15,
            "startTimeOffset": 10349014,
            "endTimeOffset": 11491003
          },
          {
            "id": 23014,
            "filter": "thumb=23014",
            "tag": "The War of the Worlds - Book 1 - Chapter 16",
            "index": 16,
            "startTimeOffset": 11491003,
            "endTimeOffset": 13191009
          },
          {
            "id": 23015,
            "filter": "thumb=23015",
            "tag": "The War of the Worlds - Book 1 - Chapter 17",
            "index": 17,
            "startTimeOffset": 13191009,
            "endTimeOffset": 14565009
          },
          {
            "id": 23016,
            "filter": "thumb=23016",
            "tag": "The War of the Worlds - Book 2 - Chapter 01",
            "index": 18,
            "startTimeOffset": 14565009,
            "endTimeOffset": 15575009
          },
          {
            "id": 23017,
            "filter": "thumb=23017",
            "tag": "The War of the Worlds - Book 2 - Chapter 02",
            "index": 19,
            "startTimeOffset": 15575009,
            "endTimeOffset": 16732018
          },
          {
            "id": 23018,
            "filter": "thumb=23018",
            "tag": "The War of the Worlds - Book 2 - Chapter 03",
            "index": 20,
            "startTimeOffset": 16732018,
            "endTimeOffset": 17524008
          },
          {
            "id": 23019,
            "filter": "thumb=23019",
            "tag": "The War of the Worlds - Book 2 - Chapter 04",
            "index": 21,
            "startTimeOffset": 17524008,
            "endTimeOffset": 18109007
          },
          {
            "id": 23020,
            "filter": "thumb=23020",
            "tag": "The War of the Worlds - Book 2 - Chapter 05",
            "index": 22,
            "startTimeOffset": 18109007,
            "endTimeOffset": 18563012
          },
          {
            "id": 23021,
            "filter": "thumb=23021",
            "tag": "The War of the Worlds - Book 2 - Chapter 06",
            "index": 23,
            "startTimeOffset": 18563012,
            "endTimeOffset": 19146021
          },
          {
            "id": 23022,
            "filter": "thumb=23022",
            "tag": "The War of the Worlds - Book 2 - Chapter 07",
            "index": 24,
            "startTimeOffset": 19146021,
            "endTimeOffset": 21473021
          },
          {
            "id": 23023,
            "filter": "thumb=23023",
            "tag": "The War of the Worlds - Book 2 - Chapter 08",
            "index": 25,
            "startTimeOffset": 21473021,
            "endTimeOffset": 22856014
          },
          {
            "id": 23024,
            "filter": "thumb=23024",
            "tag": "The War of the Worlds - Book 2 - Chapter 09",
            "index": 26,
            "startTimeOffset": 22856014,
            "endTimeOffset": 23659017
          },
          {
            "id": 23025,
            "filter": "thumb=23025",
            "tag": "The War of the Worlds - Book 2 - Chapter 10",
            "index": 27,
            "startTimeOffset": 23659017,
            "endTimeOffset": 24214697
          }
        ],
        "Extras": {
          "size": 0
        }
      }
    ]
  }
}