# sour
bitcoin/crypto app for android

# ingredients
- [schildbach wallet]: android wallet base
- [bouncy castle crypto]: java crypto library
- [bitcoinj]: google bitcoin library
- [guava]: google core libraries
- [protobuf]: google's data interchange format
- [zxing]: barcode processing library

[schildbach wallet]:https://github.com/schildbach/bitcoin-wallet
[bouncy castle crypto]:https://github.com/bcgit/bc-java
[bitcoinj]:https://github.com/bitcoinj/bitcoinj
[guava]:https://github.com/google/guava
[protobuf]:https://github.com/google/protobuf
[zxing]:https://github.com/zxing/zxing
# install build stuff
sudo apt-get install git maven openjdk-6-jdk libstdc++6:i386

# install android sdk
http://developer.android.com/sdk/

# update sdk tools
android update sdk --no-ui --force --filter tools

# install needed libraries, versions in this build filter may change
android update sdk --no-ui --force --filter build-tools-21.1.1,android-10,android-16,extra-android-m2repository

# set environment variable
export ANDROID_HOME=$HOME/android-sdk-linux/
export PATH=$PATH:$ANDROID_HOME/tools
export PATH=$PATH:$ANDROID_HOME/build-tools

# install android-build-tools
sudo apt-get install android-tools-adb

# development build
mvn clean install -Dandroid.sdk.path=<path to your android sdk>
# eclipse development
mvn clean install eclipse:eclipse -Dandroid.sdk.path=<path to your android sdk>

# production build
mvn clean install -Prelease -Dandroid.sdk.path=<path to your android sdk>
