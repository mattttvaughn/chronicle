# TODO Chronicle

## Internal

 - [ ] Update billing version by November
 - [ ] Constructor inject `Dispatchers`, remove `GlobalScope` usages (okay in Singletons?)
 - [ ] Refactor repositories to expose `StateFlow`s instead of `LiveData`?
    - [ ] Need to learn good merging techniques/extensions first
    - [ ] ViewModels too?
 - [ ] Rename callbacks in `MediaPlayerService` to be more idiomatic
 - [ ] Refactor Chapters to be managed in their own db
 - [ ] Look into `ExoPlayer.experimentalSetOffloadSchedulingEnabled`
    
## Bug fixes:

STOPSHIP:
 - [ ] 

Cannot reproduce:
 - [ ] Possible memory leaks (reported on op 6t)

Need to confirm:
 - [ ]

High priority:
 - [ ]

Medium priority: 
 - [ ] Notification doesn't get "proc"ed on chapter change
    - [ ] Temporary solution: only display track size
 - [/] issues loading huge libraries
       - [X] stopgap fix: timeout for media queries has been increased from 15s to 30s
       - [ ] real fix:
            - [ ] incremental library loading
            - [ ] optimize repository `get`s to scale better (sub n^2)
 - [ ] Playback progress in library view not updated in real-time, only on library refreshes

Low priority
 - [ ] Sleep timer "end of chapter" doesn't account for changes in playback speed
    
## High priority:

 - [X] Android Auto (feature flagged, but should be functional)
 - [ ] Casting

## Medium priority:

 - [/] Filter/Sort/`View type` UI for library
       - [/] Sort by all known fields (genre, release date, author, etc.)
             - [X] Sortable by basic fields
             - [ ] When sorting, change the grid/list item subtitle to reflect the sorted field
       - [ ] Filterable by all known Plex fields (genre, release date, author, etc.)
            - [ ] UI for this will be tricky...
       - [ ] Folder view + author view + collections view
 - [ ] Add more narrator to audiobook page: narrator
 - [ ] Better tablet/landscape support
 - [ ] Next/previous chapter buttons in "currently playing" screen?
    - [ ] At least look into improving UI for this
 - [ ] Show most recently listened book on login in bottom bar like Spotify does
 - [ ] More metadata in chapter list (start/end times or duration or something)
 - [ ] Favorites
 - [ ] Add a "last synced" at XXX warning, coupled loading data
 - [ ] Improve downloading experience
     - [ ] Show download status for tracks (progress, downloaded?)
     - [ ] Sync by disk option
     - [ ] "Download all" button?

## Low priority:

 - [ ] Support for manual server connection (no account login)
 - [ ] Handle 403s with token refresh or logout (tokens only revokable manually it seems, so not a big deal)
 - [ ] Show warning to users if they have "store track progress" disabled for library
 - [ ] Translations
 - [ ] Bookmarks + notes
 - [ ] AMOLED theme
 - [ ] Quick scroll bar
 - [ ] Widget?

## DONE:

Release 0.50:

March 15th:
 - [X] Highlight current chapter in chapter lists
 - [X] Fix issue of thumbnails from home tab not loading
 - [X] Manually set watched/unwatched
 - [X] Highlighting of current chapter
 - [X] Newly rewritten downloader
     - [X] Fix issues downloading books to SD card
     - [X] Significantly better download UX

(September 10th):
 - [X] fix: scroll position no longer lost after returning from a book
 - [X] switching servers and then starting playback will not longer cause a 401 error
 - [X] removed MINOR memory leak during the plex user chooser process
 - [X] library UI not always restoring properly (scroll state for library list lost)
 - [X] Sometimes skipping forwards will loop back to start of current track
 - [X] Seeking will no longer requires a rebuffer if content is loaded

(August 30th):
 - [X] potential track playback ordering related to disk number metadata?
 - [X] multiple M4Bs play back in incorrect order
 - [X] wired headphone media buttons possibly not working
 - [X] make more button white text
 - [X] jumping to track/chapter might accidentally rewind sometimes

(August 11th):
 - [X] added new view styles for library
 - [X] prevent network changes for force-resuming 
 - [X] fix forced scroll-to-top in library following db changes
 - [X] fixed unclearable notification issues
      - [X] added a manual "cancel notification" button just in case

(August 10th):
 - [X] fixed pause/play for wired headphones
 - [X] fixed syncing + 90% issue
 - [X] allowed multiline titles in book details screen 

(July 30th):
 - [X] Fix playback issues for multi-track audiobooks (seeking, jump to chapter)
 - [X] Fixed possible memory leak in playback
 - [X] Fixed problem where notification icon won't load on slow connections
 - [X] Possibly resolved problem where library fails to load after user pin entered 
 - [X] Increased audio buffer range
 - [X] Remote progress updates called more consistently
 - [X] Fixed 1.7x speed
 - [X] Audiobook screen now shows connection state, and allows retries on fails
 - [X] App now automatically reconnects to server on changes to network
 - [X] Better messages shown for download errors
 
 
Release 0.43:

(July 21st):
 - [X] Allow audiobooks to be retained between library changes (but not server!)

(July 10thish):
 - [X] Shake to reset snooze

Release 0.42.1:

 - [X] Bug where leading zeroes in pins are trimmed

Release 0.42:

(7/2-7/8)
 - [X] Managed users support
 - [X] Show disk groups for tracks 
 - [X] Added ability to sort books on the library screen
 - [X] App now temporarily pauses playback instead of lowering volume during temporary interruptions
 - [X] Notification now disappears upon finishing a book 
 - [X] Added option in settings to change user/server without logging out
 - [X] Warning "this will clear your downloaded books" now only appears if a user has downloaded books

(7/1)
 - [X] Remove notification after finishing playback

(6/30)
 - [X] Casting support (everything EXCEPT FOR keeping local metadata up to date)

Before 6/30 (forgot to keep up with this, but here's what I remember:)
 - [X] Group by disk
 - [X] Managed users
 - [X] Screen reader support

Release 0.40

(6/10)
 - [X] 

(6/5)
 - [X] Headphone skip forwards/back fix
 - [X] Service no longer starts from nothing when headphone buttons clicked

(6/4)
 - [X] Added auto-rewind when resuming a book after a long period of no playback
 - [X] Improved scroll performance for books with 100+ chapters/tracks
 - [X] Fixed media button issues
(6/3)
 - [X] Library default sorting uses album name, not album sort name
 - [X] Cleaning library no longer deletes synced files without warning
 - [X] Jump to track in AudiobookViewModel and CurrViewModel fixed
(6/2)
(5/28)
 - [X] Settings screen now shows user's settings
 - [X] Refactor strings to xml
 - [X] Refactor the BottomSheet stuff to be a single model
 - [X] Show pause button on book details page if it's playing

(5/27)
 - [X] Show book progress over book covers
 - [X] Allow user to disable auto-refresh
 - [X] Remove non-existing items from library on refresh

RELEASE 0.32

(5/25)
 - [x] Finished books still show up in recently listened
 - [x] changed the color of the toolbar in home and library
 - [x] removed the non-oauth login option
 - [x] changing libraries stops playback now
(5/24)
 - [x] show a warning before user clears their progress via changing chapter
 - [x] show error messages at all choose lib/server
 - [x] fix `startforeground()` not being called anr
(5/23)
 - [x] load custom chapter names
 - [x] Settings screen flashes when current track starts to maximize
 - [x] pressing home button adds search buttons + settings
 - [x] fix shared libraries access
(5/22)
 - [x] flac support

RELEASE 0.31

(5/20)
 - [X] Login via the popup thing (it's called OAuth apparently)
(5/19)
 - [X] Allow library changes w/o logging out 
(5/18)
 - [X] Book duration not showing for Cosmic Computer (m4b I think)
 - [X] Limit library updates to swipe-to-refresh + a set refresh duration to save data
 - [X] Show chapters in m4b
 - [X] Handle scrubbing via TrackListStateManager
 - [X] Fix ANR/crash from "Context.startForegroundService() did not then call Service.startForeground()"
    - [X] This will require removing the MediaServiceConnection from being a Dagger Singleton so the
          service is only running when there is music playing, paused, or buffering. I think it should
          still be scoped globally, but only start it when it needs to be started and only kill it
          when it isn't needed
 - [X] Fixed a large number of crashes- still have yet to test with managed users
Before recorded history:
 - [X] Build UI for details screen
    - [X] Add a "book info" view
 - [X] Media playback
     - [X] Review how to pass messages: service <---> activity/fragment
     - [X] Become familiar with MediaController and MediaSession APIs
     - [X] Implement Exoplayer in MediaPlayerService
     - [X] Create media notification
        - [X] Bind to service
 - [X] Implement offline media caching
 - [X] Figure out Glide integration w/ data binding if possible (idea: binding w/ two required params)
 - [X] Settings
    - [X] Create config to back app behavior
    - [X] Create settings UI
 - [X] Build "Home" view
 - [X] Refresh button on server/library chooser screens
 - [X] Prefer local servers
 - [X] Change library title text from "server" to "library"
 - [X] Add sleep timer
 - [X] Implement setting playback speed
 - [X] Make downloads to work beyond view lifecycle
 - [X] Implement sending scrobbling data to server
 - [X] Fix bottom chooser buggy in settings
 - [X] Fix bug where only single server may be used
 - [X] Weird playback speed issue for m4a/m4b (whatever frankenstein is) 
 - [X] Build UI for currently playing
     - [X] Integrate currently playing micro thing into HomeFragment and LibraryFragment
 - [X] Refactor bottom sheet into its own component
 - [X] Fix layouts to work with low-dpi/small screens
 - [X] Test servers when making queries
 - [X] SD card support
 - [X] MediaServiceConnection leak on UI destroyed
