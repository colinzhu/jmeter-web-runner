# JMeter Web Runner

JMeter Web Runner is a super simple java tool that allows you to trigger jmeter to run on the server from a web browser and see the output in real time. It uses Vert.x framework to create a web server and a websocket handler, and redirects the standard output to the web console. 

## How to use

1. Clone this repository
2. Install jmeter on the server
3. Update config.properties
4. Run the `App` class to start the server
5. Open a web browser and navigate to `http://localhost:8080`. You should see a web console with a welcome message and a prompt to enter the parameters for the task
6. Enter the JMX test file name e.g. test.jmx and click "Start"
