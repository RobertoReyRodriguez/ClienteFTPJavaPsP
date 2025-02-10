// ClientFtpProtocolService.java
import java.io.*;
import java.net.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Esta clase gestiona el canal de control del protocolo FTP.
 * Permite conectarse a un servidor FTP, enviar comandos (USER, PASS, QUIT, PWD, CWD, CDUP, PASV, LIST y RETR)
 * y recibir respuestas de forma asíncrona. Para comandos que involucran el canal de datos (LIST y RETR),
 * se utiliza una sincronización para esperar la respuesta del comando PASV.
 */
public class ClientFtpProtocolService implements Runnable {

    private Socket controlSocket;
    private BufferedReader controlReader;
    private PrintWriter controlWriter;
    private OutputStream log;
    private Thread controlThread;

    // Sincronización para el comando PASV.
    private CountDownLatch pasvLatch;
    // Socket para el canal de datos, asignado al recibir la respuesta 227.
    private Socket dataSocket;

    // Bloqueo para evitar el uso concurrente del canal de datos.
    private final Object dataChannelLock = new Object();
    private final AtomicBoolean dataChannelInUse = new AtomicBoolean(false);

    /**
     * Crea una instancia de ClientFtpProtocolService.
     *
     * @param log OutputStream utilizado para registrar comandos y respuestas (por ejemplo, System.out).
     */
    public ClientFtpProtocolService(OutputStream log) {
        this.log = log;
    }

    /**
     * Conecta con el servidor FTP usando la dirección y el puerto indicados,
     * e inicia un hilo que escucha las respuestas del servidor.
     *
     * @param server Dirección del servidor FTP.
     * @param port   Puerto del servidor FTP.
     * @throws IOException Si ocurre un error al establecer la conexión.
     */
    public void connectTo(String server, int port) throws IOException {
        controlSocket = new Socket(server, port);
        controlReader = new BufferedReader(new InputStreamReader(controlSocket.getInputStream()));
        controlWriter = new PrintWriter(controlSocket.getOutputStream(), true);
        controlThread = new Thread(this);
        controlThread.start();
    }

    /**
     * Hilo de escucha que lee las respuestas del servidor FTP de forma asíncrona.
     * Si se detecta la respuesta 227 del comando PASV, se parsea para obtener la IP y puerto del canal de datos,
     * y se notifica al hilo que espera dicha respuesta.
     */
    @Override
    public void run() {
        String line;
        try {
            while ((line = controlReader.readLine()) != null) {
                log.write((line + "\n").getBytes());
                // Si se recibe el código 227, parseamos para obtener la dirección y puerto del canal de datos.
                if (line.startsWith("227")) {
                    InetSocketAddress dataAddress = parse227(line);
                    try {
                        dataSocket = new Socket(dataAddress.getHostName(), dataAddress.getPort());
                    } catch (IOException e) {
                        log.write(("Error al conectar el canal de datos: " + e.getMessage() + "\n").getBytes());
                    }
                    if (pasvLatch != null) {
                        pasvLatch.countDown();
                    }
                }
            }
        } catch (IOException e) {
            try {
                log.write(("Error en el canal de control: " + e.getMessage() + "\n").getBytes());
            } catch (IOException ex) {
                // Ignorar
            }
        }
    }

    /**
     * Parsea la respuesta 227 para extraer la dirección IP y el puerto del canal de datos.
     * La respuesta debe tener el formato:
     * "227 Entering Passive Mode (h1,h2,h3,h4,p1,p2)."
     *
     * @param response Respuesta del servidor.
     * @return InetSocketAddress con la IP y puerto para el canal de datos.
     * @throws IOException Si la respuesta no está en el formato esperado.
     */
    private InetSocketAddress parse227(String response) throws IOException {
        Pattern pattern = Pattern.compile(".*\\((\\d+),(\\d+),(\\d+),(\\d+),(\\d+),(\\d+)\\).*");
        Matcher matcher = pattern.matcher(response);
        if (matcher.find()) {
            String ip = matcher.group(1) + "." + matcher.group(2) + "." +
                        matcher.group(3) + "." + matcher.group(4);
            int port = Integer.parseInt(matcher.group(5)) * 256 + Integer.parseInt(matcher.group(6));
            return new InetSocketAddress(ip, port);
        } else {
            throw new IOException("Respuesta PASV mal formateada: " + response);
        }
    }

    /**
     * Envía un comando al servidor FTP y lo registra en el log.
     *
     * @param command Comando FTP a enviar.
     * @throws IOException Si ocurre un error al enviar el comando.
     */
    private void sendCommand(String command) throws IOException {
        controlWriter.println(command);
        log.write((command + "\n").getBytes());
    }

    /**
     * Envía los comandos USER y PASS para autenticarse en el servidor FTP.
     *
     * @param user Nombre de usuario.
     * @param pass Contraseña.
     * @throws IOException Si ocurre un error al enviar los comandos.
     */
    public void authenticate(String user, String pass) throws IOException {
        sendCommand("USER " + user);
        sendCommand("PASS " + pass);
    }

    /**
     * Envía el comando QUIT para finalizar la sesión en el servidor FTP
     * y cierra el canal de control.
     *
     * @throws IOException Si ocurre un error al cerrar la conexión.
     */
    public void close() throws IOException {
        sendCommand("QUIT");
        if (controlSocket != null && !controlSocket.isClosed()) {
            controlSocket.close();
        }
    }

    /**
     * Envía el comando QUIT y devuelve la cadena enviada.
     *
     * @return Comando QUIT enviado.
     * @throws IOException Si ocurre un error al enviar el comando.
     */
    public String sendQuit() throws IOException {
        String command = "QUIT";
        sendCommand(command);
        return command;
    }

    /**
     * Envía el comando PWD para consultar el directorio actual
     * y devuelve la cadena enviada.
     *
     * @return Comando PWD enviado.
     * @throws IOException Si ocurre un error al enviar el comando.
     */
    public String sendPwd() throws IOException {
        String command = "PWD";
        sendCommand(command);
        return command;
    }

    /**
     * Envía el comando CWD para cambiar al directorio especificado
     * y devuelve la cadena enviada.
     *
     * @param down Directorio al que se desea cambiar.
     * @return Comando CWD enviado.
     * @throws IOException Si ocurre un error al enviar el comando.
     */
    public String sendCwd(String down) throws IOException {
        String command = "CWD " + down;
        sendCommand(command);
        return command;
    }

    /**
     * Envía el comando CDUP para subir un nivel en la estructura de directorios
     * y devuelve la cadena enviada.
     *
     * @return Comando CDUP enviado.
     * @throws IOException Si ocurre un error al enviar el comando.
     */
    public String sendCdup() throws IOException {
        String command = "CDUP";
        sendCommand(command);
        return command;
    }

    /**
     * Envía el comando PASV para solicitar la apertura de un canal de datos.
     * Se bloquea para evitar transferencias concurrentes y espera la respuesta 227
     * para crear el socket del canal de datos.
     *
     * @return Comando PASV enviado.
     * @throws IOException Si ocurre un error o si se interrumpe la espera.
     */
    public String sendPassv() throws IOException {
        // Bloquea hasta que el canal de datos esté libre.
        synchronized (dataChannelLock) {
            while (dataChannelInUse.get()) {
                try {
                    dataChannelLock.wait();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            dataChannelInUse.set(true);
        }
        pasvLatch = new CountDownLatch(1);
        String command = "PASV";
        sendCommand(command);
        try {
            pasvLatch.await(); // Espera la respuesta 227 y la creación del dataSocket.
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrumpido esperando respuesta PASV");
        }
        return command;
    }

    /**
     * Envía el comando RETR para descargar el archivo remoto indicado.
     * Se asume que previamente se ha llamado a sendPassv() para configurar el canal de datos.
     * Se lanza un hilo independiente que realiza la transferencia de datos.
     *
     * @param remote      Nombre del archivo remoto a descargar.
     * @param out         OutputStream donde se copiarán los datos (por ejemplo, FileOutputStream).
     * @param closeOutput Indica si se debe cerrar el OutputStream al finalizar la transferencia.
     * @return Comando RETR enviado.
     * @throws IOException Si el canal de datos no está iniciado o ocurre un error al enviar el comando.
     */
    public String sendRetr(String remote, OutputStream out, boolean closeOutput) throws IOException {
        String command = "RETR " + remote;
        sendCommand(command);
        if (dataSocket == null) {
            throw new IOException("Canal de datos no iniciado. Llama a sendPassv() antes de RETR.");
        }
        ClientFtpDataService dataService = new ClientFtpDataService(
                dataSocket, out, closeOutput, dataChannelLock, dataChannelInUse);
        new Thread(dataService).start();
        // Reinicia el dataSocket para permitir futuras transferencias.
        dataSocket = null;
        return command;
    }

    /**
     * Envía el comando LIST para listar los archivos/directorios en el servidor.
     * Se asume que previamente se ha llamado a sendPassv() para configurar el canal de datos.
     * Se lanza un hilo independiente que realiza la transferencia de datos.
     *
     * @param out         OutputStream donde se copiarán los datos (por ejemplo, System.out).
     * @param closeOutput Indica si se debe cerrar el OutputStream al finalizar la transferencia.
     * @return Comando LIST enviado.
     * @throws IOException Si el canal de datos no está iniciado o ocurre un error al enviar el comando.
     */
    public String sendList(OutputStream out, boolean closeOutput) throws IOException {
        String command = "LIST";
        sendCommand(command);
        if (dataSocket == null) {
            throw new IOException("Canal de datos no iniciado. Llama a sendPassv() antes de LIST.");
        }
        ClientFtpDataService dataService = new ClientFtpDataService(
                dataSocket, out, closeOutput, dataChannelLock, dataChannelInUse);
        new Thread(dataService).start();
        dataSocket = null;
        return command;
    }
}
