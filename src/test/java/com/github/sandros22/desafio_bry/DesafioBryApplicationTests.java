package com.github.sandros22.desafio_bry;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

@SpringBootTest
class DesafioBryApplicationTests {

	@Test
	@DisplayName("Deve carregar o contexto da aplicação Spring Boot")
	void contextLoads() {
	}

	@Test
	@DisplayName("Deve executar o método main sem erros")
	void mainMethodTest() {
		assertDoesNotThrow(() -> DesafioBryApplication.main(new String[]{"--server.port=0"}));
	}
}
