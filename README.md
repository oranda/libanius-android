Libanius-Android
================

Libanius is an app to aid learning. Basically it presents "quiz items" to the user, and for each one the user must select the correct answer option. Quiz items are presented at random according to a certain algorithm. An item has to be answered correctly several times before it is considered learnt.

The core use is as a vocabulary builder in a new language, but it is designed to be flexible enough to present questions and answers of all types.

The implementation is in Scala. The primary target platform is Android.

This project is the Android interface to Libanius. The core Libanius code is located here: https://github.com/oranda/libanius

Suggestions for new features and code improvements will be happily received by:

James McCabe <james@oranda.com>


Install
=======

On your Android phone, use Play Store to search for Libanius, and follow the instructions. In case you don't find it, the URL is:

https://play.google.com/store/apps/details?id=com.oranda.libanius

This includes demo data.

If you attempt to build the source, it does not work above SBT 0.12 and Java 7 due to dependency issues.


Screenshots
===========

![Libanius](https://github.com/oranda/libanius-android/raw/master/docs/screenshotQuizV05.png)
![Libanius](https://github.com/oranda/libanius-android/raw/master/docs/screenshotOptionsV072_480.png)


License
=======

Most Libanius-Android source files are made available under the terms of the GNU Affero General Public License (AGPL).
See individual files for details.

Attribution info is in [SOURCES](SOURCES.md).
