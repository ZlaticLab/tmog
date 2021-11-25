# Build and Deployment Instructions

### Install Git, Java, and Gradle

1. Install Git: [https://www.atlassian.com/git/tutorials/install-git](https://www.atlassian.com/git/tutorials/install-git)
2. Install the latest Java version 8 JDK or JRE: [https://adoptopenjdk.net/](https://adoptopenjdk.net/).
3. Install Gradle version 4.8.1 or later: [https://gradle.org/install/](https://gradle.org/install/)


### Retrieve Source Code

```bash
# clone this repository
git clone git@github.com:ZlaticLab/tmog.git

# switch to the "lmb" branch
cd tmog
git checkout lmb
```


### Build and Test Locally

The application can be built using gradle commands run from the base 
directory where the tmog repository was cloned 
(see [build.gradle](../build.gradle) for details).

```bash
gradle build

# creates:
#     build/libs/tmog-<version>.jar
#     build/libs_prod/tmog-<version>-prod.jar
```

### Create Launcher (for Use on Windows)

```bash
gradle createExe

# creates:
#     build/bin/tmog.exe
```
