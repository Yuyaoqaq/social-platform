package com.cy.share.rpc.demo.provider;

import com.cy.share.rpc.demo.api.HelloService;

public class HelloServiceImpl implements HelloService {
    @Override
    public String sayHello(String name) {
        return "Hello, " + name + "! from RPC Provider";
    }
}
