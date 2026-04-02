@REM ----------------------------------------------------------------------------
@REM Maven Wrapper startup script for Windows
@REM
@REM What this script does:
@REM   1. Locates a valid Java installation (via JAVA_HOME or PATH).
@REM   2. Checks whether the Maven Wrapper jar is present; downloads it if not.
@REM   3. Invokes the wrapper jar, which downloads the correct Maven version
@REM      if not already cached, then delegates to Maven with all arguments.
@REM
@REM Usage:
@REM   mvnw.cmd spring-boot:run   — start the application
@REM   mvnw.cmd test              — run all tests
@REM ----------------------------------------------------------------------------

@IF "%__MVNW_ARG0_NAME__%"=="" (SET __MVNW_ARG0_NAME__=%~nx0)
@SET @@ARGS=%*
@SETLOCAL

@REM ---------------------------------------------------------------------------
@REM Step 1: Locate Java
@REM ---------------------------------------------------------------------------
@SET JAVA_EXE=java.exe
@IF NOT "%JAVA_HOME%"=="" (
    @SET JAVA_EXE=%JAVA_HOME%\bin\java.exe
    @IF NOT EXIST "%JAVA_EXE%" (
        @ECHO ERROR: JAVA_HOME is set but java.exe not found at %JAVA_EXE%. >&2
        @EXIT /B 1
    )
)

@REM ---------------------------------------------------------------------------
@REM Step 2: Resolve project base directory
@REM ---------------------------------------------------------------------------
@SET MAVEN_PROJECTBASEDIR=%~dp0

@SET MAVEN_WRAPPER_JAR=%MAVEN_PROJECTBASEDIR%.mvn\wrapper\maven-wrapper.jar
@SET MAVEN_WRAPPER_PROPERTIES=%MAVEN_PROJECTBASEDIR%.mvn\wrapper\maven-wrapper.properties

@REM ---------------------------------------------------------------------------
@REM Step 3: Download wrapper jar if missing (requires PowerShell on Windows)
@REM ---------------------------------------------------------------------------
@IF NOT EXIST "%MAVEN_WRAPPER_JAR%" (
    @FOR /F "tokens=2 delims==" %%i IN ('findstr /i "wrapperUrl" "%MAVEN_WRAPPER_PROPERTIES%"') DO SET WRAPPER_URL=%%i
    @ECHO Downloading Maven Wrapper from %WRAPPER_URL%
    @powershell -Command "& { [Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12; Invoke-WebRequest -Uri '%WRAPPER_URL%' -OutFile '%MAVEN_WRAPPER_JAR%' }"
    @IF ERRORLEVEL 1 (
        @ECHO ERROR: Failed to download Maven Wrapper jar. >&2
        @EXIT /B 1
    )
)

@REM ---------------------------------------------------------------------------
@REM Step 4: Execute Maven via the wrapper jar
@REM ---------------------------------------------------------------------------
@"%JAVA_EXE%" %MAVEN_OPTS% ^
    -classpath "%MAVEN_WRAPPER_JAR%" ^
    "-Dmaven.multiModuleProjectDirectory=%MAVEN_PROJECTBASEDIR%" ^
    org.apache.maven.wrapper.MavenWrapperMain ^
    %@@ARGS%

@SET MAVEN_EXIT_CODE=%ERRORLEVEL%
@ENDLOCAL
@EXIT /B %MAVEN_EXIT_CODE%
