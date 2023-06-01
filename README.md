# JMeter Web Runner

JMeter Web Runner is a super simple java tool that allows you to trigger jmeter to run (on a server) from a web browser and see the output in real time. 

## Usage

### Option 1 - run the compiled jar directly
1. Download the latest jar from "Packages"
2. run the jar like this:
```shell
java -DjmeterHome=/home/colin/dev/apache-jmeter-5.5 -Dport=8080 -jar jmeter-web-runner-0.1.1-full.jar
```
3. Open a web browser and navigate to `http://localhost:8080` 
4. Enter the JMX test file name e.g. test.jmx and click "Start"


### Option 2 - add it as a dependency
1. Add the dependency into your project:
   ```xml
   <dependency>
       <groupId>io.github.colinzhu</groupId>
       <artifactId>jmeter-web-runner</artifactId>
       <version>0.1.1</version>
   </dependency>
   ```
2. Implement your own starter. Example: `DefaultStarter.java`
