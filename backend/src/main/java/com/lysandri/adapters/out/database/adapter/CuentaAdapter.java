package com.lysandri.adapters.out.database.adapter;
import com.lysandri.domain.ports.out.CuentaPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.List;
@Repository
public class CuentaAdapter implements CuentaPort {
 private final JdbcTemplate jdbc;
 private final TransactionTemplate transaccion;
 public CuentaAdapter(JdbcTemplate jdbc, PlatformTransactionManager manager) { this.jdbc=jdbc; this.transaccion=new TransactionTemplate(manager); }
 public boolean cambiarPassword(long usuario, String anterior, String nueva) {
  return Boolean.TRUE.equals(transaccion.execute(tx -> {
   int cambios=jdbc.update("UPDATE usuario SET passw=?, version_credenciales=version_credenciales+1, fecha_actualizacion=clock_timestamp() WHERE id_user=? AND passw=? AND activo=true AND bloqueado_admin=false AND pendiente_activacion=false",nueva,usuario,anterior);
   if(cambios!=1) return false;
   jdbc.update("UPDATE recuperacion_cuenta SET fecha_revocacion=clock_timestamp(), enlace_cifrado=NULL WHERE id_user=? AND fecha_uso IS NULL AND fecha_revocacion IS NULL",usuario);
   jdbc.update("UPDATE usuario_activacion_token SET revocado=true, fecha_revocacion=clock_timestamp() WHERE id_user=? AND usado=false AND revocado=false",usuario);
   jdbc.update("INSERT INTO aviso_cuenta(id_user,tipo) VALUES (?,'PASSWORD_CAMBIADA')",usuario);
   return true;
  }));
 }
 public List<Aviso> tomarAvisos() {
  return transaccion.execute(tx -> jdbc.query("""
   UPDATE aviso_cuenta a SET estado='ENVIANDO'
   WHERE a.id_aviso IN (SELECT id_aviso FROM aviso_cuenta WHERE estado='PENDIENTE' ORDER BY id_aviso LIMIT 5 FOR UPDATE SKIP LOCKED)
   RETURNING a.id_aviso, (SELECT email FROM usuario WHERE id_user=a.id_user), a.tipo
   """,(rs,n)->new Aviso(rs.getLong(1),rs.getString(2),rs.getString(3))));
 }
 public void confirmarAviso(long id, boolean enviado) {
  jdbc.update("UPDATE aviso_cuenta SET estado=? WHERE id_aviso=? AND estado='ENVIANDO'",enviado?"ENVIADO":"REVISAR",id);
 }
}
