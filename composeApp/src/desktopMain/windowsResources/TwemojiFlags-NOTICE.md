# Twemoji Flags

This font is a subset of Twemoji Mozilla v0.7.0, built by the Mozilla Foundation.
https://github.com/mozilla/twemoji-colr/releases/tag/v0.7.0

The flag artwork is from Twemoji, copyright 2019 Twitter, Inc. and other
contributors, under CC BY 4.0.
https://github.com/twitter/twemoji
https://creativecommons.org/licenses/by/4.0/

Husi subset the upstream font to regional indicators and subdivision flags.
The flag artwork is unchanged.

```sh
pyftsubset Twemoji.Mozilla.ttf \
  --unicodes=U+1F1E6-1F1FF,U+1F3F4,U+E0061-E007A,U+E007F \
  --output-file=TwemojiFlags.ttf
```

## License for the Code

[apache-2.0](http://www.apache.org/licenses/LICENSE-2.0)

## License for the Visual Design

The Emoji art in the twe-svg.zip archive comes from [Twemoji](https://twitter.github.io/twemoji),
and is used and redistributed under the CC-BY-4.0 [license terms](https://github.com/twitter/twemoji#license)
offered by the Twemoji project.

### Creative Commons Attribution 4.0 International (CC BY 4.0)

https://creativecommons.org/licenses/by/4.0/legalcode
