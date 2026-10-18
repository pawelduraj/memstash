# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [1.0.0] - 2026-10-19

### Added

- Quick Settings tile that opens MemStash on top of any app, so a meme gets into a chat without leaving it.
- Memes are ordinary image files in a folder picked in Android's folder picker, with no storage permission. The file name is the meme's name and subfolders work as categories.
- Search by name that ignores case and accents and forgives typos: one in words of 3 to 5 letters, two in longer ones.
- Tapping a meme copies it to the gallery or to the clipboard, as set at the bottom of the screen. Holding it opens its options, where either way can be used once.
- The gallery copy becomes the newest photo, first in the chat's gallery picker. Each copy replaces the previous one and is deleted after 1, 2, 3, 5 or 10 minutes, or when MemStash opens again; 3 minutes by default.
- Copies are the meme file unchanged and are named "memstash", so the meme's own name doesn't reach the chat.
- Folders panel for browsing subfolders, and views of all memes and of the recently copied ones.
- Rename, move and delete memes, and create, rename and delete folders, in the app.
- Add memes by sharing images to MemStash, from the photo picker, or from an image copied in another app, which MemStash offers to add.
- JPEG, PNG, GIF, WebP, HEIC, AVIF and BMP. The format is read from the file itself, and an added meme gets the extension that matches it.
- Square tiles or whole memes, switched with a button above the grid.
- Settings for hiding the meme folder from the gallery, the number of columns, sorting by name or date, showing the keyboard on open and removing MemStash from recent apps.
- About section in the settings with the version, the license, links to the source code and the privacy policy, and the licenses of the bundled libraries and font.

[1.0.0]: https://github.com/pawelduraj/memstash/releases/tag/v1.0.0
