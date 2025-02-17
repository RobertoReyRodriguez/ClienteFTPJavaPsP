import java.io.FileOutputStream;
import java.io.IOException;

/**
 * Clase de ejemplo para demostrar el uso de ClientFtpProtocolService y
 * ClientFtpDataService.
 * Realiza la conexión a un servidor FTP, la autenticación, consulta del
 * directorio actual,
 * listado de archivos y finaliza la sesión.
 * 
 * @author RoberRey
 */
public class FtpClientMain {
    public static void main(String[] args) {
        try {
            ClientFtpProtocolService ftpClient = new ClientFtpProtocolService(System.out);
            ftpClient.connectTo("ftp.dlptest.com", 21);

            ftpClient.authenticate("dlpuser", "rNrKYTX9g7z3RgJRmxWuGHbeu");

            ftpClient.sendPwd();

            ftpClient.sendPassv();
            ftpClient.sendList(System.out, false);

            // Para descargar un archivo, descomentar y ajusta el nombre y ruta del archivo.
            /*
             * ftpClient.sendPassv();
             * ftpClient.sendRetr("archivo.txt", new FileOutputStream("archivo.txt"), true);
             */
            ftpClient.sendQuit();
            ftpClient.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
