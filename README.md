# piggybank
a bitcoin wallet for android

# ingredients
- [schildbach wallet]: android wallet base
- [bouncy castle crypto]: java crypto library
- [bitcoinj]: google bitcoin library
- [guava]: google core libraries
- [protobuf]: google's data interchange format
- [zxing]: barcode processing library

# preparation

install build stuff
```sh
sudo apt-get install git maven openjdk-6-jdk libstdc++6:i386 android-tools-adb
```

download/extract android sdk from http://developer.android.com/sdk/

update sdk tools
```sh
android update sdk --no-ui --force --filter tools
```

install needed libraries, versions in this build filter may change
```sh
android update sdk --no-ui --force --filter build-tools-21.1.1,android-10,android-16,extra-android-m2repository
```

set environment variable
```sh
export ANDROID_HOME=$HOME/android-sdk-linux/
export PATH=$PATH:$ANDROID_HOME/tools
export PATH=$PATH:$ANDROID_HOME/build-tools
```

development in eclipse
```sh
mvn clean install eclipse:eclipse
```

How to Fix Unbound Classpath Variable Error in Eclipse?
- Open the Eclipse Preferences [Window - Preferences]
- Go to [Java - Build Path - Classpath Variables]
- Click New and set its name as M2_REPO
- Click Folder and select your Maven repository folder. For example, /home/magicaltux/.m2/repository


debug build
```sh
mvn clean install
```

release build
```sh
BUILD_NUMBER=1337 mvn clean install -Prelease
```

[schildbach wallet]:https://github.com/schildbach/bitcoin-wallet
[bouncy castle crypto]:https://github.com/bcgit/bc-java
[bitcoinj]:https://github.com/bitcoinj/bitcoinj
[guava]:https://github.com/google/guava
[protobuf]:https://github.com/google/protobuf
[zxing]:https://github.com/zxing/zxing

