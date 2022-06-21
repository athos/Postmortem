# Change Log
All notable changes to this project will be documented in this file. This change log follows the conventions of [keepachangelog.com](http://keepachangelog.com/).

## [Unreleased]

## [0.5.1] - 2022-06-21
### Added
- Indexed session ([#12](https://github.com/athos/Postmortem/pull/12))
- `merged-logs` ([#13](https://github.com/athos/Postmortem/pull/13))

### Fixed
- Fix override warning on Clojure 1.11 ([#16](https://github.com/athos/Postmortem/pull/16))

## [0.5.0] - 2021-03-31
### Added
- Simple logger implemented around one-shot session ([#11](https://github.com/athos/Postmortem/pull/11)

## [0.4.1] - 2021-01-14
### Added
- New `stats` fn, a short alias for `frequencies` ([#9](https://github.com/athos/Postmortem/pull/9))

## [0.4.0] - 2020-02-25
### Added
- New `frequencies` fn ([#7](https://github.com/athos/Postmortem/pull/7))

## [0.3.0] - 2020-02-06
### Added
- Multimethod support for instrumentation ([#5](https://github.com/athos/postmortem/pull/5))
- `instrument`'s new option for adding :depth to execution logs ([#6](https://github.com/athos/Postmortem/pull/6))

## [0.2.0] - 2020-01-04
### Added
- New `keys` fn to get all the log entry keys without completing any log entries ([#4](https://github.com/athos/postmortem/pull/4))

## [0.1.0] - 2019-12-09
- First release

[Unreleased]: https://github.com/athos/postmortem/compare/0.5.1...HEAD
[0.5.1]: https://github.com/athos/postmortem/compare/0.5.0...0.5.1
[0.5.0]: https://github.com/athos/postmortem/compare/0.4.1...0.5.0
[0.4.1]: https://github.com/athos/postmortem/compare/0.4.0...0.4.1
[0.4.0]: https://github.com/athos/postmortem/compare/0.3.0...0.4.0
[0.3.0]: https://github.com/athos/postmortem/compare/0.2.0...0.3.0
[0.2.0]: https://github.com/athos/postmortem/compare/0.1.0...0.2.0
[0.1.0]: https://github.com/athos/postmortem/releases/tag/0.1.0
