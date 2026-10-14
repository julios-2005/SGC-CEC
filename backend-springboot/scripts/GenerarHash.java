import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/**
 * Utilidad manual (no es un test) para generar el hash bcrypt de una
 * contraseña, útil para sembrar cuentas a mano en la base de datos.
 * Vive fuera de src/main y src/test a propósito, para que ni Maven ni
 * Surefire la compilen/ejecuten como parte del build.
 * <p>
 * Requiere el classpath de spring-security-crypto en el classpath para
 * compilar/ejecutar (ej. usando el classpath ya resuelto por Maven):
 * javac -cp "$(mvn -q dependency:build-classpath -Dmdep.outputFile=/dev/stdout)" scripts/GenerarHash.java -d scripts
 * java -cp "scripts:$(mvn -q dependency:build-classpath -Dmdep.outputFile=/dev/stdout)" GenerarHash "LaContraseñaAQui"
 */
public class GenerarHash {
    public static void main(String[] args) {
        if (args.length != 1 || args[0].isBlank()) {
            System.err.println("Uso: java GenerarHash <contraseña>");
            System.exit(1);
        }
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
        System.out.println(encoder.encode(args[0]));
    }
}
