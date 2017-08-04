package Carton;

FORM FormGerarFluxoCaixa1
{
    NullableString 	formaCalculoFaturamento;
    NullableNumeric	codGrupoEmp, codContaFaturamentoFluxo, contaSaldoInicial, contaSaldoDia, contaSaldoFluxo ;
    NullableAmount	saldoInicialFluxoCaixa;
    NullableDate 	dataSaldoInicial;
    NullableBoolean 	geradoComSucesso = false;
    
    public void salvarFluxo(NullableNumeric codGrpEmp, NullableNumeric codConta, NullableDate dataFluxo,
        NullableAmount valorPrevisto, NullableAmount valorRealizado) throws Exception
    {
        EXEC SQL
        UPDATE	<tabela>
        SET	VALOR_PREVISTO = VALOR_PREVISTO + :valorPrevisto, 
                VALOR_REALIZADO = VALOR_REALIZADO + :valorRealizado
        WHERE	COD_GRUPO_EMP = :codGrpEmp AND
                COD_CONTA_FLUXO = :codConta AND
                DATA_FLUXO = :dataFluxo;
                
        if(session.status == StatusCode.SS_NOREC)
        {
            EXEC SQL
            INSERT INTO <tabela>
                (COD_GRUPO_EMP, COD_CONTA_FLUXO, DATA_FLUXO, VALOR_PREVISTO, VALOR_REALIZADO)
            VALUES (:codGrpEmp, :codConta, :dataFluxo, :valorPrevisto, :valorRealizado);
        }
        
        if(session.status != StatusCode.SS_NORM)
        {
            session.displayToMessageBoxWait("Ocorreu um erro durante a geração do fluxo. Status: "
                + session.status + ".");
            session.queueCommand(PREVIOUS_FORM);
        }
    }
    
    COMMAND cmdGerarFluxo
    {
        NullableString codEstrutural, tipoConta, fluxoConta, codEstPesquisa;
        
        NullableNumeric	codConta, codContaTotal, numControleNf;
        
        NullableAmount valor, totalPrevisto, totalRealizado, saldoInicialPrevisto, 
            saldoInicialRealizado, valorMaiorConta;
        
        NullableAmount saldoDiaRealizadoEntrada=0, saldoDiaPrevistoEntrada=0, saldoDiaRealizadoSaida=0, saldoDiaPrevistoSaida=0;
        
        NullableDate dataInicio, dataFim, dataVencimento, dataFluxo;
        
        boolean calculouSaldoInicial;
        
        if(! session.messageBoxPrompt("Iniciar geração do Fluxo de Caixa?"))
            return;

        dataInicio = dataGeracao;    
        //dataFim = dataGeracao + prazoPrevisao;
        dataFim = dataFinal;
        
        //Exclusao dos valores do periodo
        if(tipoGeracao == "D"){
            EXEC SQL
            DELETE 	FROM <tabela>
            WHERE	COD_GRUPO_EMP = :codGrupoEmp AND
                    DATA_FLUXO BETWEEN :dataInicio AND :dataFim;
        }else{
            EXEC SQL
            DELETE 	FROM <tabela>
            WHERE	COD_GRUPO_EMP = :codGrupoEmp AND
            DATA_FLUXO BETWEEN 
              CAST(CONCAT(SUBSTRING(:dataInicio,1,8),"01")AS DATE) AND 
              date_sub(date_add(CAST(CONCAT(SUBSTRING(:dataFim,1,8),"01")AS DATE),interval 1 month),interval 1 day);
        }
        session.commitTransaction();
        
        if(! codContaFaturamentoFluxo.isNull())
        {
            if(formaCalculoFaturamento == "TR")	// tradicional
            {
                // Previsto e Realizado do Faturamento
                EXEC SQL
                SELECT 	DATA_VENCIMENTO, SUM(VALOR_DUPLICATA)
                FROM	<tabela>
                WHERE	DUPLICATA_CANCELADA = "N" AND
                        COD_EMPRESA IN
                        (SELECT	COD_EMPRESA
                        FROM	<tabela>
                        WHERE	COD_GRUPO_EMP = :codGrupoEmp) AND
                        DATA_VENCIMENTO BETWEEN :dataInicio AND :dataFim
                GROUP BY DATA_VENCIMENTO
                INTO	dataVencimento, valor
                EXECUTING
                {
                    salvarFluxo(codGrupoEmp, codContaFaturamentoFluxo , dataVencimento, valor, 0);
                }
                
                session.commitTransaction();
                
                EXEC SQL
                SELECT 	DATA_CRED_BANCO, SUM(VALOR_PAGO)
                FROM	<tabela>
                WHERE	COD_EMPRESA IN
                        (SELECT	COD_EMPRESA
                        FROM	<tabela>
                        WHERE	COD_GRUPO_EMP = :codGrupoEmp) AND
                        DATA_CRED_BANCO BETWEEN :dataInicio AND :dataFim
                GROUP BY DATA_CRED_BANCO
                INTO	dataVencimento, valor
                EXECUTING
                {
                    salvarFluxo(codGrupoEmp, codContaFaturamentoFluxo , dataVencimento, 0, valor);
                }
                
                session.commitTransaction();
            }
            else
            {
                
                            
                // Realizado do Faturamento - Opção 1 - ALTERADO, COM BASE EM FATURAMENTO 
                EXEC SQL
                SELECT <tabela>.`DATA_EMISSAO`,
                        SUM(IF(<tabela>.`OPERACAO`="E", 
                                        (<tabela>.`VALOR_TOTAL` - <tabela>.`VALOR_DESCONTO` + <tabela>.`VALOR_IPI`)*-1,
                                        (<tabela>.`VALOR_TOTAL` - <tabela>.`VALOR_DESCONTO` + <tabela>.`VALOR_IPI`))
                        ) AS TOTAL
                FROM `<tabela>` <tabela> 
                    INNER JOIN `<tabela>` <tabela> ON <tabela>.`NUM_CONTROLE` = <tabela>.`NUM_CONTROLE_NF`
                    INNER JOIN `<tabela>` <tabela> ON <tabela>.`COD_NATUREZA_OPERACAO` = <tabela>.`COD_NAT_OPERACAO`
                WHERE
                    <tabela>.`COD_EMPRESA`  IN 
                                        (SELECT E.COD_EMPRESA 
                                        FROM 	<tabela>  E
                                        WHERE	E.COD_GRUPO_EMP = :codGrupoEmp )
                AND <tabela>.`DATA_EMISSAO` BETWEEN :dataInicio AND :dataFim
                AND <tabela>.`NF_ANULADA` = "N"
                AND <tabela>.`ESTATISTICA_VENDAS` = "S"
                GROUP BY <tabela>.`DATA_EMISSAO`
                INTO	dataVencimento, valor
                EXECUTING
                {
                    salvarFluxo(codGrupoEmp, codContaFaturamentoFluxo , dataVencimento, 0, valor);
                }
                session.commitTransaction();
                  
                    
                    
                //Calcula recebimentos previstos
                if(tipoGeracao == "D")
                {
                            
                            
                            NullableNumeric diasUteis;
                            NullableAmount valorPrevDia;
                            NullableDate dia;
                            NullableNumeric codPrevisao;
                            
                            EXEC SQL
                            SELECT  COUNT(DISTINCT C.DATA_REF )
                            FROM 	<tabela> C 
                            WHERE 	C.DATA_REF BETWEEN :dataInicio AND :dataFim
                            AND 	C.TIPO = "U"
                            AND 	C.COD_EMPRESA IN 
                                    (SELECT E.COD_EMPRESA 
                                    FROM 	<tabela> E 
                                    WHERE 	E.COD_GRUPO_EMP = :codGrupoEmp )
                            INTO diasUteis;
                            
                            
                            EXEC SQL
                            SELECT  	DISTINCT C.DATA_REF
                            FROM 	<tabela> C 
                            WHERE 	C.DATA_REF BETWEEN :dataInicio AND :dataFim
                            AND 	C.TIPO = "U"
                            AND 	C.COD_EMPRESA IN 
                                        (SELECT E.COD_EMPRESA 
                                        FROM 	<tabela> E 
                                        WHERE 	E.COD_GRUPO_EMP = :codGrupoEmp )
                            INTO	dia
                            EXECUTING
                            {
                                
                                    EXEC SQL
                                    SELECT 	COD_PREVISAO_RECBTOS, COD_CONTA_FLUXO 
                                    FROM 	<tabela>
                                    INTO 	codPrevisao, codConta
                                    EXECUTING
                                    {    
                                            EXEC SQL
                                            SELECT 	SUM(F.VALOR_PREVISTO)
                                            FROM 	<tabela> F
                                            WHERE  	DATA_PREVISAO = CAST(CONCAT(SUBSTRING(:dia,1,8),"01")AS DATE)
                                            AND		F.COD_PREVISAO_RECBTOS = :codPrevisao
                                            AND 	F.COD_EMPRESA IN
                                                    (	SELECT COD_EMPRESA FROM <tabela> T WHERE T.COD_GRUPO_EMP = :codGrupoEmp)
                                            GROUP BY 	F.COD_PREVISAO_RECBTOS
                                            INTO   	valor; 
                                            
                                            valorPrevDia = valor/diasUteis;
                                            
                                            salvarFluxo(codGrupoEmp, codConta, dia, valorPrevDia, 0); 
                                    }
                            }
                            
                            
                }else{//tipo de fluxo Mensal
                        
                        NullableDate data1, data2;
                        
                        EXEC SQL
                        SELECT cast(concat(substring(:dataInicio,1,8),"01")as date),cast(concat(substring(:dataFim,1,8),"01")as date)
                        INTO   data1,data2;
                        
                        while(data1<=data2){
                        
                                NullableNumeric codPrevisao;
                                
                                EXEC SQL
                                SELECT 	COD_PREVISAO_RECBTOS, COD_CONTA_FLUXO 
                                FROM 	<tabela>
                                INTO 	codPrevisao, codConta
                                EXECUTING
                                {
                                    valor = 0; 
                                    
                                    EXEC SQL
                                    SELECT 	SUM(F.VALOR_PREVISTO)
                                    FROM 	<tabela> F
                                    WHERE  	DATA_PREVISAO = :data1
                                    AND		F.COD_PREVISAO_RECBTOS = :codPrevisao
                                    AND 	F.COD_EMPRESA IN
                                            (	SELECT COD_EMPRESA FROM <tabela> T WHERE T.COD_GRUPO_EMP = :codGrupoEmp)
                                    GROUP BY 	F.COD_PREVISAO_RECBTOS
                                    INTO   	valor; 
                                    
                                    salvarFluxo(codGrupoEmp, codConta, data1, valor, 0);
                                    
                                }
                                
                                
                                
                                session.commitTransaction();
                                
                                EXEC SQL
                                SELECT DATE_ADD(:data1, interval 1 month)
                                INTO   data1;
                                
                                
                        }
                        
                        session.commitTransaction();
                        
                }//fim do tipo de geração mensal    
            }//forma de calculo não tradicional TR
        }//codContaFaturamento not null
        
        
        
        
        
        
        
        //Previsao de Pagamentos - CONTAS A PAGAR
        EXEC SQL
        SELECT	NUM_CONTROLE_NF, DATA_VENCIMENTO, 
                (VALOR_DUPLICATA - VALOR_DESCONTO_RECEBIDO + VALOR_JUROS_A_PAGAR)
        FROM	<tabela>
        WHERE	POSICAO_DUPLICATA <> "E" AND
                COD_EMPRESA IN
                (SELECT	COD_EMPRESA
                FROM	<tabela>
                WHERE	COD_GRUPO_EMP = :codGrupoEmp) AND
                DATA_VENCIMENTO BETWEEN :dataInicio AND :dataFim
        INTO	numControleNf, dataVencimento, valor
        EXECUTING
        {
            //Verifica qual a Conta do Fluxo com o maior valor de itens
            EXEC SQL
            SELECT	<tabela>.COD_CONTA_FLUXO, SUM(<tabela>.VALOR_TOTAL) AS TOTAL_ITENS
            FROM	<tabela>
            INNER JOIN <tabela> ON (<tabela>.COD_TRANSACAO = <tabela>.COD_TRANSACAO)
            WHERE	<tabela>.COD_CONTA_FLUXO IS NOT NULL AND
                    NUM_CONTROLE_NF = :numControleNf
            GROUP BY <tabela>.COD_CONTA_FLUXO
            ORDER BY TOTAL_ITENS DESC
            LIMIT	1
            INTO	codConta, valorMaiorConta;
            
            if(session.status != StatusCode.SS_NOREC)
                salvarFluxo(codGrupoEmp, codConta, dataVencimento, valor, 0);
        }
        
        session.commitTransaction();
        
        //Realizacao de Pagamentos - CONTAS A PAGAR
        EXEC SQL
        SELECT 	<tabela>.NUM_CONTROLE_NF,
                <tabela>.DATA_PAGAMENTO, (<tabela>.VALOR_PAGO -
                <tabela>.VALOR_DESCONTO + <tabela>.VALOR_JUROS +
                <tabela>.VALOR_TARIFA_BANCO + <tabela>.VALOR_EMOLUMENTOS)
        FROM	<tabela>
        INNER JOIN <tabela> ON (<tabela>.NUM_DUPLICATA = <tabela>.NUM_DUPLICATA)
        AND (<tabela>.COD_FORNECEDOR = <tabela>.COD_FORNECEDOR)
        AND (<tabela>.COD_EMPRESA = <tabela>.COD_EMPRESA)
        AND (<tabela>.DATA_EMISSAO = <tabela>.DATA_EMISSAO)
        WHERE	<tabela>.POSICAO_DUPLICATA <> "E" AND
                <tabela>.COD_EMPRESA IN
                (SELECT	COD_EMPRESA
                FROM	<tabela>
                WHERE	COD_GRUPO_EMP = :codGrupoEmp) AND
                <tabela>.DATA_PAGAMENTO BETWEEN :dataInicio AND :dataFim
        INTO	numControleNf, dataVencimento, valor
        EXECUTING
        {
            //Verifica qual a Conta do Fluxo com o maior valor de itens
            EXEC SQL
            SELECT	<tabela>.COD_CONTA_FLUXO, SUM(<tabela>.VALOR_TOTAL) AS TOTAL_ITENS
            FROM	<tabela>
            INNER JOIN TAB_TRANSACOES_ENT ON (<tabela>.COD_TRANSACAO = <tabela>.COD_TRANSACAO)
            WHERE	<tabela>.COD_CONTA_FLUXO IS NOT NULL AND
                    NUM_CONTROLE_NF = :numControleNf
            GROUP BY <tabela>.COD_CONTA_FLUXO
            ORDER BY TOTAL_ITENS DESC
            LIMIT	1
            INTO	codConta, valorMaiorConta;
            
            if(session.status != StatusCode.SS_NOREC)
                salvarFluxo(codGrupoEmp, codConta, dataVencimento, 0, valor);
        }
        
        session.commitTransaction();
        
        // Previsao dos Outros Pagamentos - FORNECEDORES
        EXEC SQL
        SELECT	COD_CONTA_FLUXO, DATA_VENCIMENTO, 
                SUM(VALOR_PREVISTO - VALOR_DESCONTO_RECEBIDO + VALOR_JUROS_A_PAGAR)
        FROM	<tabela>
        WHERE	COD_OUTROS_PAGTOS IS NULL AND
                COD_FORNECEDOR IS NOT NULL AND
                COD_CONTA_FLUXO IS NOT NULL AND
                CANCELADO = "N" AND
                COD_EMPRESA IN
                (SELECT	COD_EMPRESA
                FROM	TAB_EMPRESAS
                WHERE	COD_GRUPO_EMP = :codGrupoEmp) AND
                DATA_VENCIMENTO BETWEEN :dataInicio AND :dataFim
        GROUP BY COD_CONTA_FLUXO, DATA_VENCIMENTO
        INTO	codConta, dataVencimento, valor
        EXECUTING
        {
            salvarFluxo(codGrupoEmp, codConta, dataVencimento, valor, 0);
        }
        
        session.commitTransaction();
        
        
        
        // Previsao dos Outros Pagamentos - NAO FORNECEDORES
        EXEC SQL
        SELECT	TAB_OUTROS_PAGTOS_CPG.COD_CONTA_FLUXO, DATA_VENCIMENTO, 
                SUM(VALOR_PREVISTO - VALOR_DESCONTO_RECEBIDO + VALOR_JUROS_A_PAGAR)
        FROM	OUTROS_PAGTOS_PREV_CPG
        INNER JOIN TAB_OUTROS_PAGTOS_CPG ON (OUTROS_PAGTOS_PREV_CPG.COD_OUTROS_PAGTOS = TAB_OUTROS_PAGTOS_CPG.COD_OUTROS_PAGTOS)
        WHERE	OUTROS_PAGTOS_PREV_CPG.COD_OUTROS_PAGTOS IS NOT NULL AND
                COD_FORNECEDOR IS NULL AND
                TAB_OUTROS_PAGTOS_CPG.COD_CONTA_FLUXO IS NOT NULL AND
                CANCELADO = "N" AND
                COD_EMPRESA IN
                (SELECT	COD_EMPRESA
                FROM	TAB_EMPRESAS
                WHERE	COD_GRUPO_EMP = :codGrupoEmp) AND
                DATA_VENCIMENTO BETWEEN :dataInicio AND :dataFim
        GROUP BY TAB_OUTROS_PAGTOS_CPG.COD_CONTA_FLUXO, DATA_VENCIMENTO
        INTO	codConta, dataVencimento, valor
        EXECUTING
        {
            salvarFluxo(codGrupoEmp, codConta, dataVencimento, valor, 0);
        }
        
        session.commitTransaction();
        
        //Realizacao dos Outros Pagamentos - NAO FORNECEDORES
        EXEC SQL
        /* código continua :)  */
		
	}
}
