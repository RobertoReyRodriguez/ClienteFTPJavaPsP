import java.io.FileOutputStream;
import java.io.IOException;

/**
 * Clase de ejemplo para demostrar el uso de ClientFtpProtocolService y ClientFtpDataService.
 * Realiza la conexión a un servidor FTP, la autenticación, consulta del directorio actual,
 * listado de archivos y finaliza la sesión.
 */
public class FtpClientMain {
    public static void main(String[] args) {
        try {
            // Se conecta a un servidor FTP aleatorio.
            ClientFtpProtocolService ftpClient = new ClientFtpProtocolService(System.out);
            ftpClient.connectTo("ftp.dlptest.com", 21);

            // Login
            ftpClient.authenticate("dlpuser", "rNrKYTX9g7z3RgJRmxWuGHbeu");

            // Consultar el directorio actual
            ftpClient.sendPwd();

            // Listar archivos: se utiliza PASV y luego LIST.
            ftpClient.sendPassv();
            ftpClient.sendList(System.out, false);

            // Para descargar un archivo, descomentar y ajusta el nombre y ruta del archivo.
            /*
            ftpClient.sendPassv();
            ftpClient.sendRetr("archivo.txt", new FileOutputStream("archivo.txt"), true);
            */
            ftpClient.sendQuit();
            ftpClient.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}

