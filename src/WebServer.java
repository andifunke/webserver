import java.io.* ;
import java.net.* ;
import java.util.* ;

public final class WebServer {

    public static HashMap<String, String> mimeTable = new HashMap<>();
    private static String mimePath = new File("mime.types").getAbsolutePath();

    public static void main(String argv[]) throws Exception {

        // Check for correct parameters
        if (argv.length == 2 && argv[0].equals("-mime"))
            // Set path to mime-table definition file
            mimePath = argv[1];
        else if (argv.length != 0) {
            System.out.println("Kein gültiger Parameter\n");
            System.exit(0);
        }
        // Parse mime-table list
        createMimeTable();

        // Set the port number.
        int port = 6789;
        // Establish the listen socket.
        ServerSocket serverSocket = null;
        try {
            serverSocket = new ServerSocket(port);
        } catch (IOException e) {
            // Check if port is already in use
            System.out.println("Port " + port + " derzeit nicht verfügbar.");
            System.exit(0);
        }
        System.out.println("WebServer running...\n");

        // Process HTTP service requests in an infinite loop.
        while (true) {
            Socket client;
            try {
                // Listen for a TCP connection request.
                client = serverSocket.accept();
                try {
                    // Construct an object to process the HTTP request message.
                    HttpRequest request = new HttpRequest(client);
                    // Create a new thread to process the request.
                    Thread thread = new Thread(request);
                    // Start the thread.
                    thread.start();
                } catch (Exception e) {
                    System.out.println("Server error: could not process HTTP request.");
                }
            } catch (IOException e) {
                System.out.println("Could not connect to client-socket.");
            }
        }
    }

    /**
     * read mime-table file into hash-map.
      */
    private static void createMimeTable() {
        String line;
        FileReader fr = null;
        BufferedReader br = null;
        try {
            fr = new FileReader(mimePath);
            if (fr != null) {
                br = new BufferedReader(fr);
                while ((line = br.readLine()) != null) {
                    StringTokenizer tokens = new StringTokenizer(line);
                    if (tokens.hasMoreTokens()) {
                        String value = tokens.nextToken();
                        if (!(value.startsWith("#") || value.equals(""))) {
                            while (tokens.hasMoreTokens()) {
                                mimeTable.put(tokens.nextToken(), value);
                            }
                        }
                    }
                }
            }
        } catch (IOException e) {
            System.out.println("Mime-Table nicht gefunden.\n");
            System.exit(0);
        } finally {
            try {
                if (br != null) br.close();
            } catch (IOException e) { e.getMessage(); }
            try {
                if (fr != null) fr.close();
            } catch (IOException e) { e.getMessage(); }
        }
    }
}


final class HttpRequest implements Runnable {

    private final static String CRLF = "\r\n";
    private Socket socket;
    private List<String[]> headerLineList = new LinkedList<>();
    private String statusLine = null;
    private String contentTypeLine = null;
    private String entityBody = null;
    private DataOutputStream os;
    private InputStreamReader isr;
    private BufferedReader br;
    private boolean fileRequested = false;
    private boolean fileExists = false;
    private String userAgent = "unknown";

    /**
     * Constructor
     * @param socket        method expects client's socket
     * @throws Exception
     */
    public HttpRequest(Socket socket) throws Exception {
        this.socket = socket;
        // Get a reference to the socket's input and output streams.
        InputStream is = socket.getInputStream();
        os = new DataOutputStream(socket.getOutputStream());
        // Set up input stream filters.
        isr = new InputStreamReader(is);
        br = new BufferedReader(isr);
    }

    /**
     * Implement the run() method of the Runnable interface.
     */
    public void run() {
        try { processHttpRequest(); } catch (Exception e) { e.printStackTrace(); }
    }

    /**
     * process HTTP/1.0 request and close connection afterwards
     * @throws Exception
     */
    private void processHttpRequest() throws Exception {

        System.out.println("New connection established by client.");
        // Get and display the header lines.
        String headerLine;
        System.out.println("------------------");
        System.out.println("Request:");
        // Read all header-lines into a list of string-arrays (one node per line).
        try {
            while (socket != null && (headerLine = br.readLine()).length() != 0) {
                // Write line to output.
                System.out.println(headerLine);
                // remember the client's user-agent
                if (headerLine.toLowerCase().startsWith("user-agent: "))
                    userAgent = headerLine.replaceFirst("User-Agent: ", "");
                // Create tokens from header-line
                headerLineList.add(headerLine.split("\\s"));
            }
        } catch (SocketException e) {
            System.out.println("Connection closed by client.\n");
        } catch (NullPointerException e) { }
        System.out.println("------------------");

        // Check for correct request-line parameters
        if (headerLineList.size() == 0 || headerLineList.get(0).length < 2 || headerLineList.get(0).length > 3)
            badRequest();
        else if (headerLineList.get(0).length == 3 && !(headerLineList.get(0)[2].startsWith("HTTP/")))
            badRequest();
        else if (!(headerLineList.get(0)[1].startsWith("/")))
            badRequest();
        // Understand request-method and address accordingly
        else {
            boolean obeyFileRequest = true;
            switch (headerLineList.get(0)[0]) {
                case "GET":
                    System.out.println("Method used: GET");
                    fileRequested = true;
                    break;
                case "HEAD":
                    System.out.println("Method used: HEAD");
                    fileRequested = true;
                    obeyFileRequest = false;
                    break;
                case "POST":
                    System.out.println("Method used: POST");
                    notImplemented();
                    break;
                default:
                    System.out.println("Method used: NULL");
                    notImplemented();
            }
            if (fileRequested) {
                // format abs_path to work with Windows and Linux OS.
                String fileName = new File(headerLineList.get(0)[1].replaceFirst("/", "")).getAbsolutePath();
                // Open the requested file.
                FileInputStream fis = null;
                // Check if resource is available and if so send as entity-body
                try {
                    fis = new FileInputStream(fileName);
                    fileExists = true;
                    ok(fileName);
                    if (obeyFileRequest) sendBytes(fis, os);
                    try { fis.close(); } catch (IOException e) { e.printStackTrace(); }
                } catch (FileNotFoundException e) {
                    notFound();
                }
            }
        }

        // Close streams and socket.
        try { os.close(); } catch (IOException e) { }
        try { br.close(); } catch (IOException e) { }
        try { isr.close(); } catch (IOException e) { }
        try {
            if (socket != null) {
                socket.close();
                System.out.println("Connection closed by server.\n");
            }
            else System.out.println("Connection closed by client.\n");
        } catch (IOException e) { System.out.println("Connection closed by client.\n"); }
    }

    private void sendResponse() {
        try {
            if (socket != null) {
                System.out.println("Response:");
                // Send the status line.
                os.writeBytes(statusLine);
                System.out.print(statusLine);
                // Send the content type line.
                os.writeBytes(contentTypeLine);
                System.out.print(contentTypeLine);
                System.out.println("------------------");
                // Send a blank line to indicate the end of the header lines.
                os.writeBytes(CRLF);
                // Send predefined entity-body if no ressource is to be send
                if (!fileExists) os.writeBytes(entityBody);
            }
            else System.out.println("Connection closed by client.\n");
        } catch (IOException e) {
            System.out.println("Server error: could not send response.");
        }
    }

    private static void sendBytes(FileInputStream fis, OutputStream os)
            throws Exception {
        // Construct a 1K buffer to hold bytes on their way to the socket.
        byte[] buffer = new byte[1024];
        int bytes = 0;
        // Copy requested file into the socket's output stream.
        while((bytes = fis.read(buffer)) != -1 )
            os.write(buffer, 0, bytes);
    }

    /**
     * read mime-type from hash-map based upon file-ending as key
     * @param fileName  method expects full file name (full path not needed)
     * @return          returns mime-type as string
     */
    private static String contentType(String fileName) {
        String mimeType;
        String fileExtension = fileName.substring(fileName.lastIndexOf(".")+1);
        if ((mimeType = WebServer.mimeTable.get(fileExtension)) != null)
            return mimeType;
        return "application/octet-stream";
    }

    // 200 OK
    private void ok(String fileName) {
        responseTemplate("200", "OK", contentType(fileName));
    }

    // 400 Bad Request
    private void badRequest() {
        responseTemplate("400", "Bad Request", "text/html");
    }

    // 404 Not Found
    private void notFound() {
        responseTemplate("404", "Not Found", "text/html");
    }

    // 501 Not Implemented
    private void notImplemented() {
        responseTemplate("501", "Not Implemented", "text/html");
    }

    /**
     * write predefined response-header and entity body
     * @param code      status code number
     * @param message   status code message
     * @param mime      response header content-type
     */
    private void responseTemplate(String code, String message, String mime) {
        statusLine = "HTTP/1.0 " + code + " " + message + CRLF;
        contentTypeLine = "Content-type: " + mime + CRLF;
        entityBody = "<HTML>\n" +
                "<HEAD>\n" +
                "<TITLE>" + message + "</TITLE>\n" +
                "</HEAD>\n" +
                "<BODY>\n" +
                "<h3>" + message + "</h3>\n" +
                "<p>Client-IP: " + socket.getInetAddress().getHostAddress() + "</p>\n" +
                "<hp>User-Agent: " + userAgent + "</p>\n" +
                "</BODY>\n" +
                "</HTML>";
        sendResponse();
    }
}

// TODO:
// Testen unter Linux
// Testen mit verschiedenen Browsern und Telnet
// Request Headerlines mit mehreren Whitespace-Zeichen testen
// URL Decoding
// POST implementieren (s.u.a. 7.2.2 - content-length), Response 201, content-length s. 10.4
// Conditional GET s. 8.1 und 10.9
