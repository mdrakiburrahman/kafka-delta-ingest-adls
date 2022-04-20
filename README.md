# kafka-delta-ingest-adls: Java

## Build and run locally

Run locally:
```bash
cd /workspaces/kafka-delta-ingest-adls/src/main/java/com/mycompany/app
java App.java
```

Run via Maven with `java -cp`:
```bash
/workspaces/kafka-delta-ingest-adls
mvn clean package
java -cp target/my-app-1.0-SNAPSHOT.jar com.mycompany.app.App
```

Run as standalone jar:
```bash
/workspaces/kafka-delta-ingest-adls
mvn clean package
java -jar target/my-app-1.0-SNAPSHOT.jar
```

## Debug locally

1. **Edit:**
   - Open `src/main/java/com/mycompany/app/App.java`.
   - Try adding some code and check out the language features.
   - Notice that the Java extension pack is already installed in the container since the `.devcontainer/devcontainer.json` lists `"vscjava.vscode-java-pack"` as an extension to install automatically when the container is created.
2. **Terminal:** Press <kbd>Ctrl</kbd>+<kbd>Shift</kbd>+<kbd>\`</kbd> and type `uname` and other Linux commands from the terminal window.
3. **Build, Run, and Debug:**
   - Open `src/main/java/com/mycompany/app/App.java`.
   - Add a breakpoint.
   - Press <kbd>F5</kbd> to launch the app in the container.
   - Once the breakpoint is hit, try hovering over variables, examining locals, and more.
4. **Run a Test:**
   - Open `src/test/java/com/mycompany/app/AppTest.java`.
   - Put a breakpoint in a test.
   - Click the `Debug Test` in the Code Lens above the function and watch it hit the breakpoint.
