import java.io.* ;
import java.net.* ;
import java.util.* ;

public final class WebServer {

    public static HashMap<String, String> mimeTable = new HashMap<>();
    private static String mimePath = new File("mime.types").getAbsolutePath();

    public static void main(String argv[]) throws Exception {
        if (argv.length == 2 && argv[0].equals("-mime"))
            mimePath = argv[1];
        else if (argv.length != 0) {
            System.out.println("Kein gültiger Parameter\n");
            System.exit(0);
        }
        createMimeTable();
        // Set the port number.
        int port = 6789;
        // Establish the listen socket.
        ServerSocket serverSocket = null;
        try {
            serverSocket = new ServerSocket(port);
        } catch (IOException e) {
            System.out.println("Port " + port + " derzeit nicht verfügbar.");
            System.exit(0);
        }
        System.out.println("WebServer running...\n");

        // Process HTTP service requests in an infinite loop.
        while (true) {
            //System.out.println("test"+ (++i));
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
                    e.printStackTrace();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

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
                //System.out.println(mimeTable);
            }
        } catch (IOException e) {
            System.out.println("Mime-Table nicht gefunden.\n");
            System.exit(0);
        } finally {
            try {
                if (br != null) br.close();
            } catch (IOException e) {}
            try {
                if (fr != null) fr.close();
            } catch (IOException e) {}
        }
    }
}

final class HttpRequest implements Runnable
{
    final static String CRLF = "\r\n";
    private Socket socket;
    private List<String[]> headerLineList = new LinkedList<>();
    private String statusLine = null;
    private String contentTypeLine = null;
    private String entityBody = null;

    // Constructor
    public HttpRequest(Socket socket) throws Exception {
        this.socket = socket;
    }

    // Implement the run() method of the Runnable interface.
    public void run() {
        try {
            processHttpRequest();
        } catch (Exception e) {}
    }

    private void processHttpRequest() throws Exception {
        // Get a reference to the socket's input and output streams.
        InputStream is = socket.getInputStream();
        DataOutputStream os = new DataOutputStream(socket.getOutputStream());
        // Set up input stream filters.
        InputStreamReader isr = new InputStreamReader(is);
        BufferedReader br = new BufferedReader(isr);

        // Get and display the header lines.
        String headerLine = null;
        //System.out.println("Header Line: ");
        while ((headerLine = br.readLine()).length() != 0) {
            System.out.println(headerLine);
            headerLineList.add(headerLine.split("\\s"));
        }
        boolean fileExists = false;

        if (headerLineList.get(0).length != 3)
            badRequest();
        else if (!(headerLineList.get(0)[2].startsWith("HTTP/")))
            badRequest();
        else if (!(headerLineList.get(0)[1].startsWith("/")))
            badRequest();
        else {
            switch (headerLineList.get(0)[0]) {
                case "GET":
                    System.out.println("Method used: GET");
                    get();
                    break;
                case "HEAD":
                    System.out.println("Method used: HEAD");
                    notImplemented();
                    break;
                case "PUT":
                    System.out.println("Method used: PUT");
                    notImplemented();
                    break;
                case "POST":
                    System.out.println("Method used: POST");
                    notImplemented();
                    break;
                case "DELETE":
                    System.out.println("Method used: DELETE");
                    notAllowed();
                    break;
                default:
                    System.out.println("Method used: NULL");
                    badRequest();
            }
        }

        // Prepend a "." so that file request is within the current directory.
        // fileName = "." + fileName; ToDo
        String fileName = headerLineList.get(0)[1];
        fileName = "C:\\Dropbox\\Entwicklung\\WebServer\\out\\production\\WebServer\\" + fileName;
        // System.out.println(fileName);
        // Open the requested file.
        FileInputStream fis = null;
        try {
            fis = new FileInputStream(fileName);
            fileExists = true;
        } catch (FileNotFoundException e) {}

        // Construct the response message.
        if (fileExists) ok(fileName);
        else notFound();

        // Send the status line.
        os.writeBytes(statusLine);
        System.out.print("------------------\n"+statusLine);
        // Send the content type line.
        os.writeBytes(contentTypeLine);
        System.out.print(contentTypeLine+"------------------\n\n");
        // Send a blank line to indicate the end of the header lines.
        os.writeBytes(CRLF);
        // Send the entity body.
        if (fileExists)	{
            sendBytes(fis, os); // ToDo evtl. Exceptions catchen
            fis.close();
        }
        else os.writeBytes(entityBody);

        // Close streams and socket.
//        is.close();
        try { os.close(); } catch (IOException e) { }
        try { br.close(); } catch (IOException e) { }
        try { socket.close(); } catch (IOException e) { }
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

    private static String contentType(String fileName) {
        String mimeType;
        String fileExtension = fileName.substring(fileName.lastIndexOf(".")+1);
        if ((mimeType = WebServer.mimeTable.get(fileExtension)) != null)
            return mimeType;
        return "application/octet-stream";
    }

    private void get() {
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

    // 405 Method Not Allowed
    private void notAllowed() {
        responseTemplate("405", "Method Not Allowed", "text/html");
    }

    // 501 Not Implemented
    private void notImplemented() {
        responseTemplate("501", "Not Implemented", "text/html");
    }

    private void responseTemplate(String code, String message, String mime) {
        statusLine = "HTTP/1.0 " + code + " " + message + CRLF;
        contentTypeLine = "Content-type: " + mime + CRLF;
        entityBody = "<HTML>" +
                "<HEAD><TITLE>" + message + "</TITLE></HEAD>" +
                "<BODY>" + message + "</BODY></HTML>";
    }

}