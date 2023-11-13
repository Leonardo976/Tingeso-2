package TopEducation.m2.service;

import TopEducation.m2.entity.CuotaEntity;
import TopEducation.m2.model.EstudiantesModel;
import TopEducation.m2.repository.CuotaRepository;
import lombok.Generated;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDate;
import java.util.ArrayList;

@Service
public class CuotaService {
    @Autowired
    CuotaRepository cuotaRepository;

    @Autowired
    RestTemplate restTemplate;

    public ArrayList<CuotaEntity> ObtenerCuotasPorRutEstudiante(String Rut) {
        /* Búsqueda de ID de estudiante */
        ResponseEntity<EstudiantesModel> responseEntity = restTemplate.exchange(
                "http://backend-gateway-service:8080/student/ByRut/" + Rut,
                HttpMethod.GET,
                null,
                new ParameterizedTypeReference<EstudiantesModel>(){}
        );

        /* Se verifica que el estudiante exista */
        if (responseEntity.getStatusCode() != HttpStatus.OK || responseEntity.getBody() == null) {
            /* Se crea estructura con 1 elemento */
            ArrayList<CuotaEntity> listafinal = new ArrayList<CuotaEntity>();
            CuotaEntity cuotaEntity = new CuotaEntity();
            cuotaEntity.setMeses_atra(-1);
            listafinal.add(cuotaEntity);

            return listafinal;
        } else {
            /* Búsqueda de conjunto de cuotas por ID estudiante */
            return cuotaRepository.findAllByEstudianteId(responseEntity.getBody().getId_estudiante());
        }
    }

    public CuotaEntity BuscarPorID(Long idCuota){ return cuotaRepository.findByIdCuota(idCuota);}

    public CuotaEntity RegistrarEstadoDePagoCuota(Long idCuota){
        /*Se busca cuentas existentes*/
        CuotaEntity CuotaExistente = cuotaRepository.findByIdCuota(idCuota);

        /*Se verifica que no se modifique una cuota en estado pagado*/
        if("Pagado".equals(CuotaExistente.getEstado())) { /*Cuota ya pagada*/
            return CuotaExistente;
        }

        /*Se verifica estado de la cuota*/
        if("Atrasada".equals(CuotaExistente.getEstado())) { /*Cuota atrasada*/
            /*Actualización de estado*/
            CuotaExistente.setEstado("Pagado (con atraso)");
        }
        else {
            CuotaExistente.setEstado("Pagado");
        }

        /*Cambio de fechas*/
        CuotaExistente.setFecha_crea(CuotaExistente.getFecha_pago());
        CuotaExistente.setFecha_pago(LocalDate.now());

        /*Retorno*/
        return cuotaRepository.save(CuotaExistente);
    }

    public ArrayList<CuotaEntity> GenerarCuotasDeEstudiante(String Rut, Integer Cantidad, String Tipo) {
        /*Elementos Internos*/
        CuotaEntity errorCuota = new CuotaEntity(); //Modelo de cuotas para errores.
        CuotaEntity ModeloCuota;    //Entidad que sirve como modelo de cuota.
        ArrayList<CuotaEntity> cuotasGeneradas = new ArrayList<>(); //Arreglo de salida.
        CuotaEntity Matricula; //Registro de matricula como primer pago
        float ArancelReal;

        /* Se busca usuario para generar cuotas */
        ResponseEntity<EstudiantesModel> responseEntity = restTemplate.exchange(
                "http://backend-gateway-service:8080/student/ByRut/" + Rut,
                HttpMethod.GET,
                null,
                new ParameterizedTypeReference<EstudiantesModel>(){}
        );

        /*Control de entrada*/
        //Existencia previa de cuotas.
        /*Rut no registrado*/
        if(responseEntity.getStatusCode() != HttpStatus.OK || responseEntity.getBody() == null){
            errorCuota.setMeses_atra(-6);
            cuotasGeneradas.add(errorCuota);
            return cuotasGeneradas;
        }
        //Existencia previa de cuotas.
        else if(!cuotaRepository.findAllByEstudianteId(responseEntity.getBody().getId_estudiante()).isEmpty()){
            errorCuota.setMeses_atra(-2);
            cuotasGeneradas.add(errorCuota);
            return cuotasGeneradas;
        }
        else if(Cantidad > 1 && Tipo.equals("Contado")){ //Más de una cuota al contado.
            /*Se entrega un arreglo de 1 elemento que establece el error*/
            errorCuota.setMeses_atra(-1);
            cuotasGeneradas.add(errorCuota);
            return cuotasGeneradas;
        }
        //Bloque de condiciones por número máximo de cuotas.
        else if(Cantidad > 10 && responseEntity.getBody().getTipo_cole().equals("Municipal")){
            errorCuota.setMeses_atra(-3);
            cuotasGeneradas.add(errorCuota);
            return cuotasGeneradas;
        }
        else if(Cantidad > 7 && responseEntity.getBody().getTipo_cole().equals("Subvencionado")){
            errorCuota.setMeses_atra(-4);
            cuotasGeneradas.add(errorCuota);
            return cuotasGeneradas;
        }
        else if(Cantidad > 4 && responseEntity.getBody().getTipo_cole().equals("Privado")){
            errorCuota.setMeses_atra(-5);
            cuotasGeneradas.add(errorCuota);
            return cuotasGeneradas;
        }

        /*Se establece primera cuota como pago de matricula*/
        Matricula = new CuotaEntity();
        Matricula.setId_estudiante(responseEntity.getBody().getId_estudiante());
        Matricula.setMonto_primario((float) (70000));   //Valor de matricula.
        Matricula.setTipo_pag("Contado");
        Matricula.setEstado("Pagado");
        Matricula.setMonto_pagado((float) 70000);
        Matricula.setFecha_crea(LocalDate.now());
        Matricula.setFecha_pago(LocalDate.now());
        Matricula.setMeses_atra(0);
        cuotasGeneradas.add(Matricula);
        cuotaRepository.save(Matricula);

        /*Se establecen valores de descuentos*/
        ArancelReal = (float) 1500000;

        /*Descuento por procedencia*/
        switch (responseEntity.getBody().getTipo_cole()) {
            case "Municipal" -> ArancelReal = ArancelReal - ((float) (1500000 * 0.20));
            case "Subvencionado" -> ArancelReal = ArancelReal - ((float) (1500000 * 0.10));
            default -> {
            }
        }

        /*Descuento por años de egreso en el colegio*/
        if(responseEntity.getBody().getAnio_egre() == 0){
            ArancelReal = ArancelReal -  ((float) (1500000*0.15));
        }
        else if(responseEntity.getBody().getAnio_egre() <= 2){
            ArancelReal = ArancelReal -  ((float) (1500000*0.08));
        }
        else if(responseEntity.getBody().getAnio_egre() <= 4){
            ArancelReal = ArancelReal -  ((float) (1500000*0.04));
        }

        /* Se ingresan cuotas a la lista */
        if(Tipo == "Contado") {
            ModeloCuota = new CuotaEntity();
            ModeloCuota.setId_estudiante(responseEntity.getBody().getId_estudiante());
            ModeloCuota.setMonto_primario((float) (1500000 / 2));
            ModeloCuota.setTipo_pag("Contado");
            ModeloCuota.setEstado("Pendiente");
            ModeloCuota.setMonto_pagado((float) 1500000/2);
            ModeloCuota.setFecha_crea(LocalDate.now());
            ModeloCuota.setMeses_atra(0);

            cuotaRepository.save(ModeloCuota); // Guarda la cuota en la base de datos
            cuotasGeneradas.add(ModeloCuota); // Agrega la cuota a la lista de cuotas generadas
        }
        else {
            /*Otros casos*/
            for (int i = 0; i < Cantidad; i++) {
                /* Se establece modelo de cuotas */
                ModeloCuota = new CuotaEntity();
                ModeloCuota.setId_estudiante(responseEntity.getBody().getId_estudiante());
                ModeloCuota.setMonto_primario((float) (1500000 / Cantidad));
                ModeloCuota.setTipo_pag("Cuotas");
                ModeloCuota.setEstado("Pendiente");
                ModeloCuota.setMonto_pagado(ArancelReal / Cantidad);
                ModeloCuota.setFecha_crea(LocalDate.now());
                ModeloCuota.setMeses_atra(0);

                cuotaRepository.save(ModeloCuota); // Guarda la cuota en la base de datos
                cuotasGeneradas.add(ModeloCuota); // Agrega la cuota a la lista de cuotas generadas
            }
        }

        /* Se retorna la lista de cuotas generadas */
        return cuotasGeneradas;
    }

    public ArrayList<CuotaEntity> ObtenerTodas(){
        return (ArrayList<CuotaEntity>) cuotaRepository.findAll();
    }

    @Generated
    public ArrayList<CuotaEntity> ActualizarCuotas(ArrayList<CuotaEntity> Cuotas){
        return (ArrayList<CuotaEntity>) cuotaRepository.saveAll(Cuotas);
    }
}
