package com.upse.inscripciones.service;
import com.upse.inscripciones.entity.*;
import com.upse.inscripciones.exception.ValidacionException;
import java.math.*;
import java.util.*;
/**
 * Regla del CEC: la transferencia internacional (25%) es un costo ADICIONAL
 * que asume el CEC, no una deducción del honorario del especialista. El
 * especialista extranjero recibe su honorario bruto completo; el egreso que
 * se registra en el Informe Económico por esa edición es honorario + 25%
 * (ej. honorario $100 → egreso $125: $100 al especialista + $25 de costo de
 * transferencia).
 */
public final class HonorariosEspecialistas {
    private HonorariosEspecialistas() {}
    /** pagoNeto = honorario + costoTransferencia: el costo TOTAL de este especialista para el CEC. */
    public record Pago(Long idEspecialista,String nombre,String paisNacionalidad,BigDecimal honorario,
                       BigDecimal costoTransferencia,BigDecimal pagoNeto) {}
    public static boolean esExtranjero(Especialista e) {
        String p=e.getPaisNacionalidad();return p!=null && !p.isBlank() && !"EC".equalsIgnoreCase(p.trim());
    }
    public static List<Pago> desglosar(Planificacion p) {
        var ds=p.getDocentesEfectivos();var valores=p.getHonorarios();
        if(ds.size()>1 && (valores==null || valores.size()!=ds.size() || ds.stream().anyMatch(d->!valores.containsKey(d.getId())))) return List.of();
        List<Pago> resultado=new ArrayList<>();
        for(var d:ds){
            BigDecimal bruto=valores==null?null:valores.get(d.getId());
            if(bruto==null) bruto=p.getCostoEspecialista()==null?BigDecimal.ZERO:p.getCostoEspecialista();
            bruto=bruto.setScale(2,RoundingMode.HALF_UP);
            BigDecimal transferencia=esExtranjero(d)?bruto.multiply(new BigDecimal("0.25")).setScale(2,RoundingMode.HALF_UP):new BigDecimal("0.00");
            resultado.add(new Pago(d.getId(),d.getNombres()+" "+d.getApellidos(),d.getPaisNacionalidad(),bruto,transferencia,bruto.add(transferencia)));
        }
        return resultado;
    }
    public static Map<Long,BigDecimal> validar(List<Especialista> ds,List<BigDecimal> importes,BigDecimal total,Map<Long,BigDecimal> anteriores){
        if(importes==null){
            if(anteriores!=null && anteriores.size()==ds.size() && ds.stream().allMatch(d->anteriores.containsKey(d.getId()))){
                BigDecimal suma=anteriores.values().stream().reduce(BigDecimal.ZERO,BigDecimal::add);
                if(total==null || total.compareTo(suma)==0)return new LinkedHashMap<>(anteriores);
            }
            if(ds.size()==1)return Map.of(ds.get(0).getId(),total==null?BigDecimal.ZERO:total);
            if(ds.stream().anyMatch(HonorariosEspecialistas::esExtranjero))throw new ValidacionException("Indica el honorario individual de cada especialista para calcular la transferencia del especialista extranjero.");
            return Map.of();
        }
        if(importes.size()!=ds.size())throw new ValidacionException("Debe haber un honorario por cada especialista seleccionado.");
        Map<Long,BigDecimal> resultado=new LinkedHashMap<>();BigDecimal suma=BigDecimal.ZERO;
        for(int i=0;i<ds.size();i++){
            BigDecimal v=importes.get(i);
            if(v==null || v.signum()<0 || v.stripTrailingZeros().scale()>2 || v.compareTo(new BigDecimal("99999999.99"))>0)
                throw new ValidacionException("Cada honorario debe ser no negativo y tener hasta dos decimales.");
            resultado.put(ds.get(i).getId(),v.setScale(2));suma=suma.add(v);
        }
        if(suma.compareTo(new BigDecimal("99999999.99"))>0)throw new ValidacionException("El total de honorarios supera el máximo permitido.");
        if(total!=null && total.compareTo(suma)!=0)throw new ValidacionException("El costo total debe coincidir con la suma de los honorarios individuales.");
        return resultado;
    }
}
