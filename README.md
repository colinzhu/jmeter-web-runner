# JMeter Web Runner

A web-based application for uploading, executing, and managing JMeter performance tests.

## Features

- **JMeter Setup Management** - Upload and configure JMeter distributions directly through the web interface (no manual installation required)
- Upload JMeter JMX test files through a web interface
- Execute tests on the server with queue management
- View generated HTML reports in the browser
- Download complete test reports as ZIP archives
- Manage uploaded test files
- Support for concurrent test executions with configurable limits

## Prerequisites

### Web-Based Setup

The application includes a web-based setup page that allows you to upload and configure JMeter without manual installation or PATH configuration.

**Required Software:**
1. **Java 17 or higher**
   - Download from [Oracle](https://www.oracle.com/java/technologies/downloads/) or [OpenJDK](https://openjdk.org/)
   - Verify installation: `java -version`

2. **Maven** (for building from source)
   - Download from [https://maven.apache.org/download.cgi](https://maven.apache.org/download.cgi)
   - Verify installation: `mvn -version`

**JMeter Setup:**
- Download JMeter ZIP from [https://jmeter.apache.org/download_jmeter.cgi](https://jmeter.apache.org/download_jmeter.cgi)
- Use the Setup page in the application to upload and configure it (see Setup Page section below)

## Configuration

The application can be configured through `src/main/resources/application.properties`:

### Server Configuration
```properties
# Server port (default: 8080)
server.port=8080
```

### File Upload Configuration
```properties
# Enable multipart file uploads
spring.servlet.multipart.enabled=true

# Maximum file size for JMeter setup uploads (default: 200MB)
spring.servlet.multipart.max-file-size=200MB

# Maximum request size (default: 200MB)
spring.servlet.multipart.max-request-size=200MB
```

### Storage Configuration
```properties
# Directory for uploaded JMX files (default: storage/uploads)
app.storage.upload-dir=storage/uploads

# Directory for generated reports (default: storage/reports)
app.storage.report-dir=storage/reports
```

### JMeter Configuration
```properties
# Maximum number of concurrent test executions (default: 2)
app.jmeter.max-concurrent-executions=2

# JMeter installation path (set automatically via Setup page)
app.jmeter.installation-path=

# JMeter version (detected automatically via Setup page)
app.jmeter.version=
```

### Logging Configuration
```properties
# Log level for application (default: INFO)
logging.level.io.github.colinzhu.webrunner=INFO
```

## Building and Running

### Build the Application
```bash
# Clean and build
mvn clean package

# Run tests
mvn test

# Skip tests during build
mvn clean package -DskipTests
```

### Run the Application
```bash
# Using Maven
mvn spring-boot:run

# Using the JAR file
java -jar target/jmeter-web-runner-1.0.0.jar

# Using the JAR file with a custom port
java -jar target/jmeter-web-runner-1.0.0.jar --server.port=8081
```

### Access the Application
Open your browser and navigate to:
```
http://localhost:8080
```

## Storage Directory Setup

The application automatically creates the required storage directories on startup:
- `storage/uploads/` - Stores uploaded JMX files
- `storage/reports/` - Stores generated test reports

These directories are created relative to the application's working directory. You can customize the paths in `application.properties`.

### Manual Directory Creation (Optional)
```bash
# Create storage directories manually if needed
mkdir -p storage/uploads
mkdir -p storage/reports
```

## Usage

### Setup Page (First-Time Configuration)

If you haven't manually installed JMeter or want to use the web-based setup:

#### 1. Access the Setup Page
- Navigate to the "Setup" link in the application navigation
- Or visit: `http://localhost:8080/setup.html`

#### 2. Check JMeter Status
- The page displays whether JMeter is currently configured
- If configured, it shows the JMeter version and installation path
- Click "Check Status" to refresh the current state

#### 3. Upload JMeter Distribution
- Download a JMeter ZIP file from [Apache JMeter Downloads](https://jmeter.apache.org/download_jmeter.cgi)
- Click "Choose File" or drag-and-drop the ZIP file onto the upload area
- Click "Upload and Configure" to begin the setup process
- The system will:
  - Validate the ZIP file contains a valid JMeter distribution
  - Extract JMeter to the application directory
  - Detect and configure the JMeter version
  - Make JMeter available for test executions

#### 4. Setup Confirmation
- Upon successful setup, the page displays:
  - JMeter version detected
  - Installation path
  - Confirmation that JMeter is ready for use

#### 5. Replace Existing Installation (Optional)
- If JMeter is already configured, you can upload a new distribution to replace it
- The system will warn you before replacing the existing installation
- Replacement is blocked if test executions are currently running

#### Notes:
- Maximum upload size: 200MB
- Only `.zip` files are accepted
- Both Unix and Windows JMeter distributions are supported
- The configuration persists across application restarts

### Working with Test Files

#### 1. Upload a JMX File
- Click "Choose File" and select a `.jmx` file
- Click "Upload" to upload the file to the server
- The file will appear in the "Uploaded Files" list

#### 2. Execute a Test
- Find your uploaded file in the list
- Click the "Execute" button
- The execution will be queued and processed automatically
- Monitor the execution status in the "Executions" panel

#### 3. View Reports
- Once an execution completes successfully, a "View Report" link appears
- Click to view the HTML report in your browser
- Reports include detailed performance metrics and graphs

#### 4. Download Reports
- Click the "Download" button next to a completed execution
- A ZIP archive containing all report files will be downloaded
- The archive includes HTML files, CSV data, and all assets

#### 5. Manage Files
- Click "Delete" next to any uploaded file to remove it
- Deleted files cannot be recovered

## API Endpoints

### Setup Management
- `GET /api/setup/status` - Get current JMeter setup status
- `POST /api/setup/upload` - Upload and configure JMeter distribution
- `DELETE /api/setup/installation` - Remove current JMeter installation
- `POST /api/setup/verify` - Verify JMeter installation

### File Management
- `POST /api/files` - Upload a JMX file
- `GET /api/files` - List all uploaded files
- `DELETE /api/files/{id}` - Delete a file

### Execution Management
- `POST /api/executions` - Start a test execution
- `GET /api/executions` - List all executions
- `GET /api/executions/{id}` - Get execution status

### Report Access
- `GET /api/reports/{id}` - View report HTML
- `GET /api/reports/{id}/download` - Download report as ZIP

## Troubleshooting

### Setup Page Issues

#### JMeter Upload Fails
**Error**: `Invalid JMeter distribution`

**Solution**:
1. Ensure you downloaded the correct ZIP file from Apache JMeter
2. The ZIP must contain a `bin` directory with `jmeter` (Unix) or `jmeter.bat` (Windows)
3. Don't extract the ZIP - upload the original ZIP file
4. Verify the ZIP file is not corrupted

#### Upload Size Limit Exceeded
**Error**: `File size exceeds maximum limit`

**Solution**:
- The default maximum upload size is 200MB
- If your JMeter distribution is larger, increase the limit in `application.properties`:
```properties
spring.servlet.multipart.max-file-size=300MB
spring.servlet.multipart.max-request-size=300MB
```

#### Cannot Replace JMeter Installation
**Error**: `Cannot replace JMeter while test executions are active`

**Solution**:
- Wait for all running test executions to complete
- Check the main page for any executions in "RUNNING" or "QUEUED" status
- Once all executions are complete, try the replacement again

#### JMeter Version Not Detected
**Error**: `Failed to detect JMeter version`

**Solution**:
1. Verify the uploaded ZIP contains a valid JMeter distribution
2. Check that the JMeter binary has executable permissions (Unix/Linux)
3. Try re-uploading the JMeter distribution
4. Check application logs for detailed error information

### JMeter Not Found (Manual Installation)
**Error**: `JMeter not found in system PATH`

**Solution**:
1. Use the Setup page to upload JMeter (recommended), OR
2. Verify JMeter is installed manually: `jmeter -v`
3. Ensure JMeter's `bin` directory is in your PATH
4. Restart your terminal/command prompt after updating PATH
5. Restart the application

### File Upload Fails (Test Files)
**Error**: `File size exceeds maximum limit`

**Solution**:
- The maximum file size for test files is 200MB (same as JMeter setup uploads)
- If you need to upload larger test files, increase the limit in `application.properties`:
```properties
spring.servlet.multipart.max-file-size=300MB
spring.servlet.multipart.max-request-size=300MB
```

### Execution Fails
**Error**: `JMeter execution failed with exit code: 1`

**Possible Causes**:
1. Invalid JMX file format
2. Missing test dependencies
3. JMeter configuration issues

**Solution**:
- Verify the JMX file works in JMeter GUI
- Check execution error messages in the UI
- Review application logs for detailed error information

### Port Already in Use
**Error**: `Port 8080 is already in use`

**Solution**:
- Change the port in `application.properties`:
```properties
server.port=8081
```

### Storage Permission Issues
**Error**: `Failed to store file`

**Solution**:
- Ensure the application has write permissions to the storage directories
- Check directory ownership and permissions
- On Linux/Mac: `chmod 755 storage/`

## Development

### Running Tests
```bash
# Run all tests
mvn test

# Run specific test class
mvn test -Dtest=FileControllerPropertyTest

# Run with verbose output
mvn test -X
```

### Project Structure
```
src/
├── main/
│   ├── java/io/github/colinzhu/jmeter/webrunner/
│   │   ├── config/          # Configuration classes
│   │   ├── controller/      # REST API controllers
│   │   ├── exception/       # Exception handling
│   │   ├── model/           # Data models
│   │   ├── repository/      # Data repositories
│   │   └── service/         # Business logic services
│   └── resources/
│       ├── application.properties
│       └── static/          # Web frontend files
└── test/
    └── java/io/github/colinzhu/jmeter/webrunner/
        ├── controller/      # Controller tests
        └── service/         # Service tests
```

### Technology Stack
- **Backend**: Spring Boot 3.2.0, Java 17
- **Testing**: JUnit 5, jqwik (property-based testing)
- **Build**: Maven
- **Frontend**: HTML, CSS, JavaScript (vanilla)
