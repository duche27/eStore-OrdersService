# eStore-OrdersService

Desde el microservicio que va a hacer de hilo conductor, que es el que recibe las llamadas por API:

queryController recibe DTO y crea QUERY - (QueryGateway) - @queryHandler(validaciones/filtros) va a BD y devuelve la info

commandController recibe DTO y crea COMMAND con @TargetAggregateIdentifier en campo necesario - (CommandGateway) 
  - Interceptor (si hace falta, como en PRODUCTS) comprueba la existencia en BD de la entidad que se va a crear/modificar/eliminar en una tabla lookup de campos reducidos)
  - Aggregate(validaciones) crea EVENT(@AggregateIdentifier en campo necesario marcado con @TargetAggregateIdentifier en COMMAND, actualiza campos de aggregate) y publica AggregateLifecycle.apply:
    - EventHandler recibe @EventHandler on(OrderProcessedEvent) y persiste en BD
  - SAGA hace @SagaEventHandler(associationProperty = "orderId" que es el campo en común de todos los eventos y comandos que pasan por la clase SAGA) handle(xEvent) crea COMMAND/QUERY que sea con la info del EVENT, y manda por CommandGateway/QueryGateway al bus que le llegará a un AGGREGATE o a un HANDLER si es QUERY
    - cada vez que se envía un COMMAND/QUERY, se prepara un método void onResult que si recibe excepción/error ejecuta ROLLBACKS desde SAGA

