@echo off
setlocal enabledelayedexpansion

cd /d "%~dp0"

echo Building LawAI microservices...
call mvnw.cmd -q install -DskipTests
if errorlevel 1 exit /b 1

start "auth-service" cmd /c "cd auth-service && ..\mvnw.cmd -q spring-boot:run"
timeout /t 8 /nobreak >nul
start "billing-service" cmd /c "cd billing-service && ..\mvnw.cmd -q spring-boot:run"
timeout /t 4 /nobreak >nul
start "legal-service" cmd /c "cd legal-service && ..\mvnw.cmd -q spring-boot:run"
timeout /t 4 /nobreak >nul
start "document-service" cmd /c "cd document-service && ..\mvnw.cmd -q spring-boot:run"
timeout /t 4 /nobreak >nul
start "research-service" cmd /c "cd research-service && ..\mvnw.cmd -q spring-boot:run"
timeout /t 4 /nobreak >nul
start "platform-service" cmd /c "cd platform-service && ..\mvnw.cmd -q spring-boot:run"
timeout /t 4 /nobreak >nul
start "api-gateway" cmd /c "cd api-gateway && ..\mvnw.cmd -q spring-boot:run"

echo.
echo All services starting. API Gateway: http://localhost:8080
echo   auth-service      :8081
echo   billing-service   :8082
echo   legal-service     :8083
echo   document-service  :8084
echo   research-service  :8085
echo   platform-service  :8086
