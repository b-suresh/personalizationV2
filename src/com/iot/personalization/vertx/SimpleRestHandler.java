package com.iot;

import com.iot.personalization.CustomerIdentity;
import com.iot.personalization.DummyCustomerService;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;

public class SimpleRestHandler extends AbstractVerticle {
	
	public void start() {
		Router router = Router.router(vertx);
		router.route().handler(BodyHandler.create());
		router.get("/greet/:rfid").handler(this::handleGetGreeting);
		
		vertx.createHttpServer().requestHandler(router::accept).listen(8080);
	}
	
	private void handleGetGreeting(RoutingContext routingContext){
		String rfid = routingContext.request().getParam("rfid");
	    HttpServerResponse response = routingContext.response();
	    if (rfid == null) {
	        sendError(400, response);
	      } 
	    else {
	    	CustomerIdentity identity = new CustomerIdentity();
			identity.rfid = rfid;
			String name = new DummyCustomerService().getCustomerNameFromCustomerIdentity(identity);
	        
	        if (name == null) {
	        	sendError(404, response);
	        } 
	        else {
	        	JsonObject greeting = new JsonObject().put("greeting","Hello "+name);
	        	response.putHeader("content-type", "application/json").end(greeting.encodePrettily());
	        }
	      }
	}

	private void sendError(int statusCode, HttpServerResponse response) {
		response.setStatusCode(statusCode).end();
	}
	
	public static void main(String args[]){
		Vertx vertx = Vertx.vertx();
		vertx.deployVerticle(new SimpleRestHandler());
	}
}
