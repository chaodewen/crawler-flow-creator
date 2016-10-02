package mo.cc.server;

import org.jboss.resteasy.plugins.server.netty.NettyJaxrsServer;
import org.jboss.resteasy.spi.ResteasyDeployment;

public class RestEasyNettyServer {
	private NettyJaxrsServer netty;
	private ResteasyDeployment deployment;
	private String port;

	public RestEasyNettyServer(ResteasyDeployment deployment
			, String port) {
		this.netty = new NettyJaxrsServer();
		this.deployment = deployment;
		this.port = port;
	}

	public void start() throws Exception {
		netty.setDeployment(deployment);
		netty.setPort(Integer.parseInt(port));
		netty.setSecurityDomain(null);
		netty.start();
	}
	
	public void stop(){
		netty.stop();
	}
}