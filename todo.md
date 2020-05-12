# TODO Chronicle

5 minutes or less:
 - [ ] 
 
Bug fixes:
 - [ ] 

High priority:
 - [ ] IAP
 - [ ] Fix anr/crash from "Context.startForegroundService() did not then call Service.startForeground()"

Medium priority:
 - [ ] Handle scrubbing via TrackListStateManager
 - [ ] Support for local authentication skipping
 - [ ] Remove non-existing items from library on refresh
 - [ ] Testing (a few comprehensive UI tests- all w/ fake data)
    - [ ] Business as usual: network available, test login, playback, controls, and caching
    - [ ] Offline: no network, some offlined books already
    - [ ] Some kind of test on a faked web server?
 - [ ] Chromecast support 
 - [/] Android auto support
 - [ ] Filter UI
 - [ ] Download all UI
 
Low priority
 - [ ] Refactor server/library choosers to use shared view/logic
 - [ ] Refactor strings to xml
 - [ ] Quick scroll bar

DONE:

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
 - [X] Lower network request frequency
