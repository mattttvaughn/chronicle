# Chronicle Audiobook Player

The best Android Audiobook Player for Plex. Stream or download audiobooks hosted on your Plex server. 

### Features

 - Sync audiobook progress on device
 - Support for file formats: mp3, m4a, m4b
 - Adjustable playback speed
 - Auto-rewind
 - Sleep timer
 - Skip silent audio
 - Download books for playing any time, even when offline
 - Basic Android Auto support (playback works but no voice support)
 
### Screenshots

<p float="left">
<img src="https://raw.githubusercontent.com/mattttvaughn/chronicle/develop/images/home.png" alt="Home screen" height="200">
<img src="https://raw.githubusercontent.com/mattttvaughn/chronicle/develop/images/library.png" alt="Library screen" height="200">
<img src="https://raw.githubusercontent.com/mattttvaughn/chronicle/develop/images/currentlyplaying.png" alt="Player" height="200">
</p>


#### Useful Links

 - [Plex Audiobook Guide](https://github.com/seanap/Plex-Audiobook-Guide)
 - [Chronicle subreddit](https://www.reddit.com/r/ChronicleApp/)





### Developer Notes

#### Ktlint
Ktlint is a linting tool that is based on the [kotlin style guide](https://developer.android.com/kotlin/style-guide). It will validate and make sure that your code adheres to that style guide.

The [ktlint gradle plugin](https://github.com/jlleitschuh/ktlint-gradle) adds the ktlintCheck and ktlintFormat tasks to gradle.
- `ktlintCheck` - checks the source code for issues and outputs a report to `app/build/reports/ktlint` 
- `ktlintFormat` - autoformats the code based on the kotlin style guide.

Ktlint check and format can be run on the code base by running the following commands in the root of the project.
```
./gradlew ktlintCheck
./gradlew ktlintFormat
```

##### Git hook
A git hook has also been added. This basically runs the ktlintCheck every time a user tries to commit. If it finds violations it prompts the user to run ktlintFormat and fix any inconsistencies with the style guide. The purpose of this is to make sure that everyone is adhering to the style guide and writing clean code.

