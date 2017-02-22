package com.iot.personalization.vertx;

import java.util.function.Consumer;

import com.hazelcast.config.Config;
import com.hazelcast.config.NetworkConfig;
import com.iot.personalization.Customer;
import com.iot.personalization.CustomerIdentity;
import com.iot.personalization.CustomerService;
import com.iot.personalization.DummyCustomerService;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.json.JsonObject;
import io.vertx.core.shareddata.AsyncMap;
import io.vertx.core.shareddata.SharedData;
import io.vertx.core.spi.cluster.ClusterManager;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.spi.cluster.hazelcast.HazelcastClusterManager;

public class SimpleRestHandler extends AbstractVerticle {
	
	CustomerService customerService = new DummyCustomerService();
	SharedData sharedData;
	AsyncMap<String, String> map;
	
	public SimpleRestHandler(){
		System.out.println("Beginning to deploy SimpleRestHandler...");
		VertxOptions options = buildVertxOptions();
		Vertx.clusteredVertx(options, res -> {
		    if (res.succeeded()) {
		        Vertx vertx = res.result();
		        this.sharedData = vertx.sharedData();
		        System.out.println("Deployed SimpleRestHandler and set the shared data!");
		        addToSharedMap("CustomerService", new DummyCustomerService());
		        System.out.println("Set the shared customer service object!!");
		    }
		});
	}
	
	public SimpleRestHandler( SharedData sharedData ){
		this.sharedData = sharedData;
		addToSharedMap("CustomerService", new DummyCustomerService());
	}
	
	public void start() {
		Router router = Router.router(vertx);
		router.route().handler(BodyHandler.create());
		router.get("/greet/:rfid").handler(this::handleGetGreeting);
		router.post("/customer/add").handler(this::handleAddGreeting);
		
		vertx.createHttpServer().requestHandler(router::accept).listen(7070);
	}
	
	private void handleGetGreeting(RoutingContext routingContext){
		String rfid = routingContext.request().getParam("rfid");
	    HttpServerResponse response = routingContext.response();
	    if (rfid == null) {
	        sendError(400, response);
	      } 
	    else {
	    	CustomerIdentity identity = new CustomerIdentity();
			identity.setRfid(rfid);
			processCustomer( customerService -> {
				String name = customerService.getCustomerNameFromCustomerIdentity(identity);
		        
		        if (name == null) {
		        	System.out.println("name was null for "+identity.getRfid());
		        	sendError(404, response);
		        } 
		        else {
		        	JsonObject greeting = new JsonObject().put("greeting","Hello "+name);
		        	response.putHeader("content-type", "application/json").end(greeting.encodePrettily());
		        }
			});
	      }
	}
	
	private void handleAddGreeting(RoutingContext routingContext){
		JsonObject inputJSon = routingContext.getBodyAsJson();
	    HttpServerResponse response = routingContext.response();
	    if (inputJSon == null) {
	        sendError(400, response);
	      } 
	    else {
	    	String customerId = inputJSon.getString("customerId");
	    	String rfId = inputJSon.getString("rfId");
	    	if( isNullorEmpty(customerId) && isNullorEmpty(rfId) ){
	    		sendError(400, response);
	    	}
	    	String name = inputJSon.getString("name");
	    	CustomerIdentity identity = new CustomerIdentity();
	    	identity.setCustomerId(customerId);
	    	identity.setRfid(rfId);
	    	Customer customer = new Customer(identity, name);
	    	processCustomer(customerService -> customerService.addCustomer(customer));
	    	JsonObject responseJSon = new JsonObject().put("greeting","Hello "+name);
	        response.putHeader("content-type", "application/json").end(responseJSon.encodePrettily());
	      }		
	}
	
	private void addToSharedMap(String key, Object value) {
		sharedData.<String, Object>getClusterWideMap("mymap", res -> {
			  if (res.succeeded()) {
			    AsyncMap<String, Object> map = res.result();
			    map.put(key, value, resPut -> {
			    	  if (resPut.succeeded()) {
			    		  // Successfully put the value
			    		  System.out.println("Successfully put the DummyCustomerService");
			    	  } else {
			    		  // Something went wrong!
			    		  System.out.println("Failed putting the DummyCustomerService in clustered map");
			    	  }
			    	});
			  } else {
			    // Something went wrong!
				  System.out.println("Could not get the clustered map");
			  }
			});
	}	
	
	private void processCustomer(Consumer<CustomerService> func) {
		sharedData.<String, Object>getClusterWideMap("mymap", res -> {
			  if (res.succeeded()) {
			    AsyncMap<String, Object> map = res.result();
			    map.get("CustomerService", resGet -> {
			    	  if (resGet.succeeded()) {
			    		  // Successfully put the value
			    		  System.out.println("Successfully retrieved from clustered map:key:CustomerService");
			    		  CustomerService customerService = (CustomerService)resGet.result();
			    		  func.accept(customerService);
			    		  addToSharedMap("CustomerService",customerService);
			    	  } else {
			    		  // Something went wrong!
			    		  System.out.println("Failed retrieving CustomerService from clustered map");
			    	  }
			    	});
			  } else {
			    // Something went wrong!
			  }
			});
	}
	
	private boolean isNullorEmpty(String str){
		if(str == null || str.trim().equals("")) return true;
		return false;
	}

	private void sendError(int statusCode, HttpServerResponse response) {
		response.setStatusCode(statusCode).end();
	}
	
	public static void main(String args[]){
		System.out.println("Beginning to deploy SimpleRestHandler...");
		VertxOptions options = buildVertxOptions();
		Vertx.clusteredVertx(options, res -> {
		    if (res.succeeded()) {
		        Vertx vertx = res.result();
		        SharedData sd = vertx.sharedData();
		        vertx.deployVerticle(new SimpleRestHandler(sd));
		        System.out.println("Deployed SimpleRestHandler!");
		    }
		});
	}

	/**
	 * 
	 */
	protected static VertxOptions buildVertxOptions() {
        
        Config hazelcastConfig = new Config();
        NetworkConfig networkConfig = hazelcastConfig.getNetworkConfig();
        String localIp = System.getenv("LOCAL_IP");
        System.out.println("localIp:"+localIp);
        String leaderIp = System.getenv("SURESH1_PORT_5701_TCP_ADDR");
        if(leaderIp == null || leaderIp.trim().equals("")){
        	leaderIp = localIp;
        }
        System.out.println("leaderIp:"+leaderIp);
        networkConfig.getJoin().getTcpIpConfig().addMember(leaderIp).setEnabled(true);
        networkConfig.getJoin().getMulticastConfig().setEnabled(false);

		ClusterManager mgr = new HazelcastClusterManager(hazelcastConfig);
		return new VertxOptions().setClusterManager(mgr);
	}
};