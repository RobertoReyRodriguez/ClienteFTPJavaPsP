// ClientFtpDataService.java
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * La clase ClientFtpDataService se encarga de gestionar el canal de datos del FTP.
 * Implementa Runnable para poder ejecutar la transferencia en un hilo independiente.
 * Realiza la copia de datos entre el InputStream del socket de datos y el OutputStream
 * indicado (por ejemplo, un FileOutputStream o System.out). Al finalizar, cierra el socket
 * y, si es necesario, el OutputStream, y libera el bloqueo que impide transferencias concurrentes.
 */
public class ClientFtpDataService implements Runnable {

    private Socket dataSocket;
    private OutputStream out;
    private boolean closeOutput;
    private final Object dataChannelLock;
    private final AtomicBoolean dataChannelInUse;

    /**
     * Crea una instancia de ClientFtpDataService.
     *
     * @param dataSocket       Socket del canal de datos ya conectado.
     * @param out              OutputStream donde se copiarán los datos recibidos.
     * @param closeOutput      Si es true, se cerrará el OutputStream al finalizar la transferencia.
     * @param dataChannelLock  Objeto de bloqueo utilizado para sincronizar el canal de datos.
     * @param dataChannelInUse Indicador de uso del canal de datos.
     */
    public ClientFtpDataService(Socket dataSocket, OutputStream out, boolean closeOutput,
                                Object dataChannelLock, AtomicBoolean dataChannelInUse) {
        this.dataSocket = dataSocket;
        this.out = out;
        this.closeOutput = closeOutput;
        this.dataChannelLock = dataChannelLock;
        this.dataChannelInUse = dataChannelInUse;
    }

    /**
     * Ejecuta la transferencia de datos copiando bytes desde el InputStream del socket
     * hacia el OutputStream indicado. Al finalizar, cierra los recursos y libera el bloqueo.
     */
    @Override
    public void run() {
        try (InputStream in = dataSocket.getInputStream()) {
            byte[] buffer = new byte[4096];
            int bytesRead;
            while ((bytesRead = in.read(buffer)) != -1) {
                out.write(buffer, 0, bytesRead);
            }
            out.flush();
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                dataSocket.close();
            } catch (IOException e) {
                // Ignorar excepciones al cerrar
            }
            if (closeOutput) {
                try {
                    out.close();
                } catch (IOException e) {
                    // Ignorar excepciones al cerrar
                }
            }
            // Libera el bloqueo para permitir nuevas transferencias en el canal de datos.
            synchronized (dataChannelLock) {
                dataChannelInUse.set(false);
                dataChannelLock.notifyAll();
            }
        }
    }
}
