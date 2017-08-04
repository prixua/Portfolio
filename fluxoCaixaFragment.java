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
        UPDATE	FLUXO_CAIXA_VALORES
        SET	VALOR_PREVISTO = VALOR_PREVISTO + :valorPrevisto, 
                VALOR_REALIZADO = VALOR_REALIZADO + :valorRealizado
        WHERE	COD_GRUPO_EMP = :codGrpEmp AND
                COD_CONTA_FLUXO = :codConta AND
                DATA_FLUXO = :dataFluxo;
                
        if(session.status == StatusCode.SS_NOREC)
        {
            EXEC SQL
            INSERT INTO FLUXO_CAIXA_VALORES
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
            DELETE 	FROM FLUXO_CAIXA_VALORES
            WHERE	COD_GRUPO_EMP = :codGrupoEmp AND
                    DATA_FLUXO BETWEEN :dataInicio AND :dataFim;
        }else{
            EXEC SQL
            DELETE 	FROM FLUXO_CAIXA_VALORES
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
                FROM	DUPLICATAS_CRB
                WHERE	DUPLICATA_CANCELADA = "N" AND
                        COD_EMPRESA IN
                        (SELECT	COD_EMPRESA
                        FROM	TAB_EMPRESAS
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
                FROM	DUPLICATAS_CRB_PAGAMENTOS
                WHERE	COD_EMPRESA IN
                        (SELECT	COD_EMPRESA
                        FROM	TAB_EMPRESAS
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
                SELECT NFISCAIS_FAT.`DATA_EMISSAO`,
                        SUM(IF(NFISCAIS_FAT.`OPERACAO`="E", 
                                        (NFISCAIS_FAT_PRODUTOS.`VALOR_TOTAL` - NFISCAIS_FAT_PRODUTOS.`VALOR_DESCONTO` + NFISCAIS_FAT_PRODUTOS.`VALOR_IPI`)*-1,
                                        (NFISCAIS_FAT_PRODUTOS.`VALOR_TOTAL` - NFISCAIS_FAT_PRODUTOS.`VALOR_DESCONTO` + NFISCAIS_FAT_PRODUTOS.`VALOR_IPI`))
                        ) AS TOTAL
                FROM `NFISCAIS_FAT` NFISCAIS_FAT 
                    INNER JOIN `NFISCAIS_FAT_PRODUTOS` NFISCAIS_FAT_PRODUTOS ON NFISCAIS_FAT.`NUM_CONTROLE` = NFISCAIS_FAT_PRODUTOS.`NUM_CONTROLE_NF`
                    INNER JOIN `TAB_NATUREZAS_OPERACAO` TAB_NATUREZAS_OPERACAO ON NFISCAIS_FAT_PRODUTOS.`COD_NATUREZA_OPERACAO` = TAB_NATUREZAS_OPERACAO.`COD_NAT_OPERACAO`
                WHERE
                    NFISCAIS_FAT.`COD_EMPRESA`  IN 
                                        (SELECT E.COD_EMPRESA 
                                        FROM 	TAB_EMPRESAS  E
                                        WHERE	E.COD_GRUPO_EMP = :codGrupoEmp )
                AND NFISCAIS_FAT.`DATA_EMISSAO` BETWEEN :dataInicio AND :dataFim
                AND NFISCAIS_FAT.`NF_ANULADA` = "N"
                AND TAB_NATUREZAS_OPERACAO.`ESTATISTICA_VENDAS` = "S"
                GROUP BY NFISCAIS_FAT.`DATA_EMISSAO`
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
                            FROM 	SISTEMAS.TAB_CALENDARIO C 
                            WHERE 	C.DATA_REF BETWEEN :dataInicio AND :dataFim
                            AND 	C.TIPO = "U"
                            AND 	C.COD_EMPRESA IN 
                                    (SELECT E.COD_EMPRESA 
                                    FROM 	TAB_EMPRESAS E 
                                    WHERE 	E.COD_GRUPO_EMP = :codGrupoEmp )
                            INTO diasUteis;
                            
                            
                            EXEC SQL
                            SELECT  	DISTINCT C.DATA_REF
                            FROM 	SISTEMAS.TAB_CALENDARIO C 
                            WHERE 	C.DATA_REF BETWEEN :dataInicio AND :dataFim
                            AND 	C.TIPO = "U"
                            AND 	C.COD_EMPRESA IN 
                                        (SELECT E.COD_EMPRESA 
                                        FROM 	TAB_EMPRESAS E 
                                        WHERE 	E.COD_GRUPO_EMP = :codGrupoEmp )
                            INTO	dia
                            EXECUTING
                            {
                                
                                    EXEC SQL
                                    SELECT 	COD_PREVISAO_RECBTOS, COD_CONTA_FLUXO 
                                    FROM 	TAB_PREVISAO_RECBTOS_CRB
                                    INTO 	codPrevisao, codConta
                                    EXECUTING
                                    {    
                                            EXEC SQL
                                            SELECT 	SUM(F.VALOR_PREVISTO)
                                            FROM 	FLUXO_PREVISAO_FATURAMENTO F
                                            WHERE  	DATA_PREVISAO = CAST(CONCAT(SUBSTRING(:dia,1,8),"01")AS DATE)
                                            AND		F.COD_PREVISAO_RECBTOS = :codPrevisao
                                            AND 	F.COD_EMPRESA IN
                                                    (	SELECT COD_EMPRESA FROM TAB_EMPRESAS T WHERE T.COD_GRUPO_EMP = :codGrupoEmp)
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
                                FROM 	TAB_PREVISAO_RECBTOS_CRB
                                INTO 	codPrevisao, codConta
                                EXECUTING
                                {
                                    valor = 0; 
                                    
                                    EXEC SQL
                                    SELECT 	SUM(F.VALOR_PREVISTO)
                                    FROM 	FLUXO_PREVISAO_FATURAMENTO F
                                    WHERE  	DATA_PREVISAO = :data1
                                    AND		F.COD_PREVISAO_RECBTOS = :codPrevisao
                                    AND 	F.COD_EMPRESA IN
                                            (	SELECT COD_EMPRESA FROM TAB_EMPRESAS T WHERE T.COD_GRUPO_EMP = :codGrupoEmp)
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
        FROM	DUPLICATAS_CPG
        WHERE	POSICAO_DUPLICATA <> "E" AND
                COD_EMPRESA IN
                (SELECT	COD_EMPRESA
                FROM	TAB_EMPRESAS
                WHERE	COD_GRUPO_EMP = :codGrupoEmp) AND
                DATA_VENCIMENTO BETWEEN :dataInicio AND :dataFim
        INTO	numControleNf, dataVencimento, valor
        EXECUTING
        {
            //Verifica qual a Conta do Fluxo com o maior valor de itens
            EXEC SQL
            SELECT	TAB_TRANSACOES_ENT.COD_CONTA_FLUXO, SUM(NFISCAIS_ENT_MATERIAIS.VALOR_TOTAL) AS TOTAL_ITENS
            FROM	NFISCAIS_ENT_MATERIAIS
            INNER JOIN TAB_TRANSACOES_ENT ON (NFISCAIS_ENT_MATERIAIS.COD_TRANSACAO = TAB_TRANSACOES_ENT.COD_TRANSACAO)
            WHERE	TAB_TRANSACOES_ENT.COD_CONTA_FLUXO IS NOT NULL AND
                    NUM_CONTROLE_NF = :numControleNf
            GROUP BY TAB_TRANSACOES_ENT.COD_CONTA_FLUXO
            ORDER BY TOTAL_ITENS DESC
            LIMIT	1
            INTO	codConta, valorMaiorConta;
            
            if(session.status != StatusCode.SS_NOREC)
                salvarFluxo(codGrupoEmp, codConta, dataVencimento, valor, 0);
        }
        
        session.commitTransaction();
        
        //Realizacao de Pagamentos - CONTAS A PAGAR
        EXEC SQL
        SELECT 	DUPLICATAS_CPG.NUM_CONTROLE_NF,
                DUPLICATAS_CPG_PAGAMENTOS.DATA_PAGAMENTO, (DUPLICATAS_CPG_PAGAMENTOS.VALOR_PAGO -
                DUPLICATAS_CPG_PAGAMENTOS.VALOR_DESCONTO + DUPLICATAS_CPG_PAGAMENTOS.VALOR_JUROS +
                DUPLICATAS_CPG_PAGAMENTOS.VALOR_TARIFA_BANCO + DUPLICATAS_CPG_PAGAMENTOS.VALOR_EMOLUMENTOS)
        FROM	DUPLICATAS_CPG_PAGAMENTOS
        INNER JOIN DUPLICATAS_CPG ON (DUPLICATAS_CPG_PAGAMENTOS.NUM_DUPLICATA = DUPLICATAS_CPG.NUM_DUPLICATA)
        AND (DUPLICATAS_CPG_PAGAMENTOS.COD_FORNECEDOR = DUPLICATAS_CPG.COD_FORNECEDOR)
        AND (DUPLICATAS_CPG_PAGAMENTOS.COD_EMPRESA = DUPLICATAS_CPG.COD_EMPRESA)
        AND (DUPLICATAS_CPG_PAGAMENTOS.DATA_EMISSAO = DUPLICATAS_CPG.DATA_EMISSAO)
        WHERE	DUPLICATAS_CPG.POSICAO_DUPLICATA <> "E" AND
                DUPLICATAS_CPG_PAGAMENTOS.COD_EMPRESA IN
                (SELECT	COD_EMPRESA
                FROM	TAB_EMPRESAS
                WHERE	COD_GRUPO_EMP = :codGrupoEmp) AND
                DUPLICATAS_CPG_PAGAMENTOS.DATA_PAGAMENTO BETWEEN :dataInicio AND :dataFim
        INTO	numControleNf, dataVencimento, valor
        EXECUTING
        {
            //Verifica qual a Conta do Fluxo com o maior valor de itens
            EXEC SQL
            SELECT	TAB_TRANSACOES_ENT.COD_CONTA_FLUXO, SUM(NFISCAIS_ENT_MATERIAIS.VALOR_TOTAL) AS TOTAL_ITENS
            FROM	NFISCAIS_ENT_MATERIAIS
            INNER JOIN TAB_TRANSACOES_ENT ON (NFISCAIS_ENT_MATERIAIS.COD_TRANSACAO = TAB_TRANSACOES_ENT.COD_TRANSACAO)
            WHERE	TAB_TRANSACOES_ENT.COD_CONTA_FLUXO IS NOT NULL AND
                    NUM_CONTROLE_NF = :numControleNf
            GROUP BY TAB_TRANSACOES_ENT.COD_CONTA_FLUXO
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
        FROM	OUTROS_PAGTOS_PREV_CPG
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
        SELECT	TAB_OUTROS_PAGTOS_CPG.COD_CONTA_FLUXO,
                OUTROS_PAGTOS_REAL_CPG.DATA_PAGAMENTO, SUM(OUTROS_PAGTOS_REAL_CPG.VALOR_PAGO -
                OUTROS_PAGTOS_REAL_CPG.VALOR_DESCONTO + OUTROS_PAGTOS_REAL_CPG.VALOR_JUROS)
        FROM	OUTROS_PAGTOS_PREV_CPG
        INNER JOIN TAB_OUTROS_PAGTOS_CPG ON (OUTROS_PAGTOS_PREV_CPG.COD_OUTROS_PAGTOS = TAB_OUTROS_PAGTOS_CPG.COD_OUTROS_PAGTOS)
        INNER JOIN OUTROS_PAGTOS_REAL_CPG ON (OUTROS_PAGTOS_PREV_CPG.COD_EMPRESA = OUTROS_PAGTOS_REAL_CPG.COD_EMPRESA)
        AND (OUTROS_PAGTOS_PREV_CPG.NUM_PREVISAO = OUTROS_PAGTOS_REAL_CPG.NUM_PREVISAO)
        WHERE	OUTROS_PAGTOS_PREV_CPG.COD_OUTROS_PAGTOS IS NOT NULL AND
                COD_FORNECEDOR IS NULL AND
                TAB_OUTROS_PAGTOS_CPG.COD_CONTA_FLUXO IS NOT NULL AND
                OUTROS_PAGTOS_PREV_CPG.CANCELADO = "N" AND
                OUTROS_PAGTOS_REAL_CPG.COD_EMPRESA IN
                (SELECT	COD_EMPRESA
                FROM	TAB_EMPRESAS
                WHERE	COD_GRUPO_EMP = :codGrupoEmp) AND
                DATA_PAGAMENTO BETWEEN :dataInicio AND :dataFim
        GROUP BY TAB_OUTROS_PAGTOS_CPG.COD_CONTA_FLUXO, DATA_PAGAMENTO
        INTO	codConta, dataVencimento, valor
        EXECUTING
        {
            salvarFluxo(codGrupoEmp, codConta, dataVencimento, 0, valor);
        }
        
        session.commitTransaction();
        
        
        
        //Calcula Previsto do "2.04.001 - COMISSÕES (74)" conforme entradas de NFs dos representantes da Nat. Operacao "1342 - SERVIÇOS DE REPRES.(COMISSÕES)"
        EXEC SQL
        SELECT 	DCPG.DATA_VENCIMENTO, SUM(DCPG.VALOR_DUPLICATA)
        FROM 	NFISCAIS_ENT ENT 
        JOIN 	NFISCAIS_ENT_MATERIAIS E ON E.NUM_CONTROLE_NF = ENT.NUM_CONTROLE 
        JOIN 	DUPLICATAS_CPG DCPG ON DCPG.NUM_CONTROLE_NF = ENT.NUM_CONTROLE
        WHERE 	E.COD_NATUREZA_OPERACAO = 1342 
        AND 	ENT.COD_EMPRESA IN (SELECT COD_EMPRESA FROM TAB_EMPRESAS T WHERE T.COD_GRUPO_EMP = :codGrupoEmp) 
        AND 	ENT.NF_CANCELADA = "N" 
        AND 	ENT.DATA_EMISSAO BETWEEN :dataInicio AND :dataFim
        GROUP BY DCPG.DATA_VENCIMENTO
        INTO	dataVencimento, valor
        EXECUTING
        {
            salvarFluxo(codGrupoEmp, 74, dataVencimento, valor, 0);
        }
        
        //Calcula Realizado do "2.04.001 - COMISSÕES" conforme entradas de NFs dos representantes da Nat. Operacao "1342 - SERVIÇOS DE REPRES.(COMISSÕES)"
        EXEC SQL
        SELECT 	DCPG_P.DATA_PAGAMENTO, SUM(DCPG_P.VALOR_PAGO)
        FROM 	NFISCAIS_ENT ENT 
        JOIN 	NFISCAIS_ENT_MATERIAIS E ON E.NUM_CONTROLE_NF = ENT.NUM_CONTROLE 
        JOIN 	DUPLICATAS_CPG DCPG ON DCPG.NUM_CONTROLE_NF = ENT.NUM_CONTROLE
        LEFT JOIN 	
                DUPLICATAS_CPG_PAGAMENTOS DCPG_P ON DCPG_P.NUM_DUPLICATA = DCPG.NUM_DUPLICATA
        AND 	DCPG_P.COD_EMPRESA = DCPG.COD_EMPRESA
        AND 	DCPG_P.COD_FORNECEDOR = DCPG.COD_FORNECEDOR
        AND 	DCPG_P.DATA_EMISSAO = DCPG.DATA_EMISSAO
        WHERE 	E.COD_NATUREZA_OPERACAO = 1342 
        AND 	ENT.COD_EMPRESA IN (SELECT COD_EMPRESA FROM TAB_EMPRESAS T WHERE T.COD_GRUPO_EMP = :codGrupoEmp) 
        AND 	ENT.NF_CANCELADA = "N" 
        AND 	ENT.DATA_EMISSAO BETWEEN :dataInicio AND :dataFim
        GROUP BY 
                DCPG_P.DATA_PAGAMENTO
        HAVING SUM(DCPG_P.VALOR_PAGO) > 0 
        INTO	dataVencimento, valor
        EXECUTING
        {
            salvarFluxo(codGrupoEmp, 74, dataVencimento, 0, valor);
        }
        
        session.commitTransaction();
                
        
        
        NullableDate data;
        for(data = dataInicio; data <= dataFim; data+=1){
        
                //Acrescenta movimento de CAIXAS E BANCOS no realizado do "2.08.015 - DESPESAS DIVERSAS (157)"    
                EXEC SQL
                SELECT 	SUM(B.VALOR) AS VALOR,B.DATA_LANCTO
                FROM 	SISTEMAS.MOVTO_CAIXA_BANCOS B
                WHERE 	B.ORIGEM_LANCTO = "CXB"
                AND 	B.CREDITO_DEBITO = "D" 
                AND 	B.COD_EMPRESA IN 
                            (SELECT COD_EMPRESA FROM TAB_EMPRESAS WHERE COD_GRUPO_EMP = :codGrupoEmp )
                AND 	B.DATA_LANCTO = :data
                AND 	B.COD_HISTORICO IN 
                            (SELECT H.COD_HISTORICO FROM TAB_HISTORICOS_CXB H WHERE H.INCLUIR_FLUXO = "S" AND H.ATIVO = "S")
                GROUP BY B.DATA_LANCTO
                INTO valor,dataVencimento;
                
                if(session.status != StatusCode.SS_NOREC)
                    salvarFluxo(codGrupoEmp, 157, dataVencimento, 0, valor);
                    
                    
                
                // Acrescenta movimento de CAIXAS E BANCOS no realizado do "1.01.002 - OUTROS RECEBIMENTOS (273)" - todos históricos de TIPO_OPERACAO='E'
                // e "1.01.003 - GARANTIAS A RECEBER (285)" - histórico 172
                EXEC SQL
                SELECT 	SUM(B.VALOR) AS VALOR,
                        B.DATA_LANCTO AS DATA, 
                        B.COD_HISTORICO
                FROM 	SISTEMAS.MOVTO_CAIXA_BANCOS B
                WHERE 	B.ORIGEM_LANCTO = "CXB"
                AND 	B.COD_EMPRESA IN 
                                        (SELECT COD_EMPRESA FROM TAB_EMPRESAS WHERE COD_GRUPO_EMP = :codGrupoEmp )
                AND 	B.DATA_LANCTO = :data
                AND 	B.COD_HISTORICO IN 
                                        (SELECT H.COD_HISTORICO FROM TAB_HISTORICOS_CXB H WHERE H.COD_HISTORICO = 172 OR H.TIPO_OPERACAO = "E")
                GROUP BY B.DATA_LANCTO, B.COD_HISTORICO
                
                UNION ALL
                
                SELECT SUM(DR.VALOR_DUPLICATA)*-1 as VALOR,
                                DB.DATA_BORDERO AS DATA, 
                                '172' AS COD_HISTORICO
                FROM 	TAB_PORTADORES P 
                JOIN 	DUPLICATAS_CRB_BORDERO DB ON DB.COD_PORTADOR = P.COD_PORTADOR
                JOIN 	DUPLICATAS_CRB DR ON DR.NUM_BORDERO = DB.NUM_BORDERO 
                AND 	DUPLICATA_CANCELADA = "N" 
                AND 	DB.DATA_BORDERO = :data
                WHERE 	P.ENTRA_EM_GARANTIA_RECEBER = "S" 
                AND 	P.ATIVO = "S" 
                AND 	P.COD_GRUPO_EMP = :codGrupoEmp
                GROUP BY DB.DATA_BORDERO
                INTO	valor, dataVencimento, codConta
                EXECUTING
                {
                    if(codConta == 172)
                        salvarFluxo(codGrupoEmp, 285, dataVencimento, 0, valor);
                    else
                        salvarFluxo(codGrupoEmp, 273, dataVencimento, 0, valor);
                        
                }
                
                session.commitTransaction();
        }
    
    
    
    
    
        
        
        
        //Calcula Pendências não pagas
        try{
                NullableAmount valor_a_pagar=0,vlr=0;
                            
                EXEC SQL
                SELECT 	D.VALOR_DUPLICATA+D.VALOR_JUROS_A_PAGAR-D.VALOR_DESCONTO_RECEBIDO AS 'VALOR'
                FROM 	DUPLICATAS_CPG D
                LEFT 	OUTER JOIN DUPLICATAS_CPG_PAGAMENTOS P
                ON 	D.COD_EMPRESA = P.COD_EMPRESA
                AND 	D.NUM_DUPLICATA = P.NUM_DUPLICATA
                AND 	D.COD_FORNECEDOR = P.COD_FORNECEDOR
                AND 	D.DATA_EMISSAO = P.DATA_EMISSAO
                JOIN 	TAB_BANCOS T ON D.NUM_BANCO = T.NUM_BANCO
                WHERE 	D.DATA_VENCIMENTO BETWEEN cast(date_sub(:dataInicio, interval 360 day)as date) 
                                                        AND :dataInicio
                AND 	D.COD_EMPRESA IN
                                (	SELECT COD_EMPRESA FROM TAB_EMPRESAS E WHERE E.COD_GRUPO_EMP = :codGrupoEmp )
                AND 	T.INCLUIR_PREVISOES = "S"
                AND 	D.POSICAO_DUPLICATA <> "E"
                GROUP BY 	D.NUM_DUPLICATA, T.NUM_BANCO, D.DATA_VENCIMENTO
                HAVING 	sum(P.VALOR_PAGO + P.VALOR_JUROS - P.VALOR_DESCONTO) IS NULL
                
                UNION ALL
                
                SELECT 	C.VALOR_PREVISTO + C.VALOR_JUROS_A_PAGAR - C.VALOR_DESCONTO_RECEBIDO AS 'VALOR'
                FROM 	OUTROS_PAGTOS_PREV_CPG C
                LEFT 	JOIN OUTROS_PAGTOS_REAL_CPG R
                ON 		C.NUM_PREVISAO =  R.NUM_PREVISAO
                AND 	C.COD_EMPRESA = R.COD_EMPRESA
                JOIN 	TAB_BANCOS T ON C.COD_BANCO = T.NUM_BANCO
                WHERE 	C.COD_EMPRESA IN 
                                (	SELECT COD_EMPRESA FROM TAB_EMPRESAS E WHERE E.COD_GRUPO_EMP = :codGrupoEmp )
                AND 	C.DATA_VENCIMENTO BETWEEN cast(date_sub(:dataInicio, interval 360 day)as date) 
                                                AND :dataInicio
                AND 	C.CANCELADO = "N"
                AND 	T.INCLUIR_PREVISOES = "S"
                GROUP BY 	C.NUM_PREVISAO, C.COD_BANCO, C.DATA_VENCIMENTO
                HAVING 	SUM(R.VALOR_PAGO + R.VALOR_JUROS - R.VALOR_DESCONTO) IS NULL
                INTO 	vlr
                EXECUTING{
                    valor_a_pagar = valor_a_pagar + vlr;
                }
                
                salvarFluxo(codGrupoEmp, (NullableNumeric)291, dataInicio, valor_a_pagar, (NullableAmount)0);
        }catch(Exception e){
        
            session.displayToMessageBoxWait("Erro ao gravar Pendências: "+e.getMessage());  
            geradoComSucesso = true;
        }
        
        /* ... */
		
	}
}