package Relat;

import net.sf.jasperreports.engine.*;
import net.sf.jasperreports.engine.export.*;
import java.sql.*;
import java.util.*;
import java.text.*;
import java.io.*;
import ClassesJava.NxjProperties;

FORM ProdutosEmProcessoMateriaisDiretos1
{

    NullableNumeric codEmpresaUsuario, codEmp;
    NullableString codUsuario, nomeEmp;
    ArrayList opcoes;
    NameValuePair obj;
    NxjProperties nxjProperties = new NxjProperties();
    String pathrel = nxjProperties.getProperty("RELATORIOS");
       
    Connection conn;
    HashMap parametros = new HashMap();
    String razaoSocial="";
    NullableBinary reportImg = null;
    
    BEFORE APPLICATION
    {
        if (session.startupProperties != null){   
            if (session.startupProperties.getProperty("usuario") != null){
                codUsuario = session.startupProperties.getProperty("usuario");
                codEmpresaUsuario = (NullableNumeric) session.startupProperties.getProperty("empresa");
            }else{
                session.queueCommand(EXIT);
            }
            
        }else{
            session.queueCommand(EXIT);
        }
     
    }

    BEFORE FORM
    {
    
        opcoes = new ArrayList();
        
        EXEC SQL
        SELECT NOME_FANTASIA, COD_EMPRESA
        FROM   <tabela>
        WHERE  COD_EMPRESA IN
                 (  SELECT COD_EMPRESA FROM <tabela>
                    WHERE  COD_USUARIO = :codUsuario )
        INTO   nomeEmp, codEmp
        EXECUTING
        {
            obj = new NameValuePair(nomeEmp,codEmp);
            opcoes.add(obj);
        }
        
        codEmpSel.setOptions(opcoes);
        codEmpSel = codEmpresaUsuario;
    
    
    }
    
    COMMAND cmdGerar
    {
        if (codEmpSel.isNull())
            session.displayToMessageBoxWait("Selecione uma Empresa.");
        else{              
            try{
                conn = session.getConnection("Sistemas").getJdbcConnection();
            }    
            catch(Exception e){
                session.displayToMessageBox("Problema Conexão " + e.toString());
            }
            
            
            EXEC SQL
            SELECT NOME_FANTASIA 
            FROM <tabela> 
            WHERE COD_EMPRESA = :codEmpSel
            INTO razaoSocial;
            
            
            //Limpa dados das tabelas
            EXEC SQL
            DELETE FROM <tabela> WHERE COD_EMPRESA = :codEmpSel;
            
            EXEC SQL
            DELETE FROM <tabela> WHERE COD_EMPRESA = :codEmpSel;
            
            
            //Guarda Produtos em Processo em tabela temporária
            NullableNumeric numOp, quantidadeOp, qtdeApontUltProcSeq, codMaterialMax, largCartaoOp, compCartaoOp, 
                            largCartaoUtiliz, compCartaoUtiliz, largCartaoCalc, compCartaoCalc, 
                            gramatura, numImagens, entradaExpedicao, quebraEfetivada, qtdeElabEstimada, emElaboracao;
            NullableString  codEmbalagem, descProduto, ultGrupoProdRoteiro, prodEncerrUltProcSeq, unidadeMed, cortarMetade;
            NullableFloat   materialPago;
            
            EXEC SQL
            SELECT
                    P.NUM_OP, 
                    P.COD_EMBALAGEM,
                    OP.DESCRICAO_PRODUTO,
                    OP.QUANTIDADE_OP,
                    IF(
                            (SELECT O.SEQUENCIA_PRODUCAO
                            FROM  <tabela> OPA
                            JOIN  <tabela> O ON O.COD_MAQ_ORCAM = OPA.COD_MAQUINA
                            WHERE OPA.NUM_CONTROLE_PLANO = P.NUM_CONTROLE_PLANO
                            and   OPA.NUM_OP = P.NUM_OP
                            AND   O.COD_GRUPO_MAQUINA NOT IN("SEL","GOF","VIS","ACO")
                            ORDER BY O.SEQUENCIA_PRODUCAO DESC, O.COD_GRUPO_MAQUINA DESC LIMIT 1)
                    >
                            IFNULL((SELECT O.SEQUENCIA_PRODUCAO
                            FROM  <tabela> O
                            JOIN  <tabela> I ON I.COD_IMPRESSORA = O.COD_MAQ_ORCAM
                            WHERE   I.NUM_OP =   P.NUM_OP
                            and   I.NUM_CONTROLE_PLANO = P.NUM_CONTROLE_PLANO
                            ORDER BY O.SEQUENCIA_PRODUCAO DESC, O.COD_GRUPO_MAQUINA DESC LIMIT 1),0)
                    ,	(SELECT  O.COD_GRUPO_MAQUINA
                            FROM  <tabela> OPA
                            LEFT JOIN  <tabela> O ON O.COD_MAQ_ORCAM = OPA.COD_MAQUINA
                            WHERE OPA.NUM_CONTROLE_PLANO =  P.NUM_CONTROLE_PLANO
                            and   OPA.NUM_OP =  P.NUM_OP
                            AND   O.COD_GRUPO_MAQUINA NOT IN("SEL","GOF","VIS","ACO")
                            ORDER BY O.SEQUENCIA_PRODUCAO DESC, O.COD_GRUPO_MAQUINA DESC LIMIT 1)
                    ,	(SELECT  O.COD_GRUPO_MAQUINA
                            FROM  <tabela> O
                            JOIN   <tabela> I ON I.COD_IMPRESSORA = O.COD_MAQ_ORCAM
                            WHERE   I.NUM_OP = P.NUM_OP
                            and   I.NUM_CONTROLE_PLANO = P.NUM_CONTROLE_PLANO
                            ORDER BY O.SEQUENCIA_PRODUCAO DESC, O.COD_GRUPO_MAQUINA DESC LIMIT 1)
                    )  AS ULT_GRUPO_PROD_ROTEIRO,
            
                    IFNULl(( SELECT VIEW_OP.TOTAL_PRODUZIDO
                    FROM  <tabela> VIEW_OP
                    WHERE VIEW_OP.NUM_OP = P.NUM_OP
                    AND   VIEW_OP.NUM_CONTROLE_PLANO = P.NUM_CONTROLE_PLANO
                    AND   VIEW_OP.COD_GRUPO_MAQUINA = (SELECT ULT_GRUPO_PROD_ROTEIRO)
                    ORDER BY VIEW_OP.SEQUENCIA_ENCERRADA DESC, VIEW_OP.SEQUENCIA_PRODUCAO DESC, VIEW_OP.SEQUENCIA_OPERACAO DESC LIMIT 1
                    ),0) AS QTD_APONTADA_ULT_PROC_E_SEQ,
            /* ocultado */
            INTO numOp, codEmbalagem, descProduto, quantidadeOp, ultGrupoProdRoteiro, qtdeApontUltProcSeq, prodEncerrUltProcSeq, 
                 codMaterialMax, materialPago, unidadeMed, largCartaoOp, compCartaoOp, cortarMetade, largCartaoUtiliz, compCartaoUtiliz,
                 gramatura, numImagens, largCartaoCalc, compCartaoCalc, entradaExpedicao, qtdeElabEstimada, quebraEfetivada, emElaboracao
            EXECUTING
            {
                    EXEC SQL
                    INSERT INTO <tabela> VALUES(:codEmpSel, :numOp, :codEmbalagem, :descProduto, :quantidadeOp, :ultGrupoProdRoteiro, 
                                :qtdeApontUltProcSeq, :prodEncerrUltProcSeq, :codMaterialMax, :materialPago, :unidadeMed, :largCartaoOp, :compCartaoOp, 
                                :cortarMetade, :largCartaoUtiliz, :compCartaoUtiliz, :gramatura, :numImagens, :largCartaoCalc, :compCartaoCalc, 
                                :entradaExpedicao, :qtdeElabEstimada, :quebraEfetivada, :emElaboracao);
                    
                    
                    
                    
                    NullableString tipoMaterial, descMaterial, unidadeMedMaterial;
                    NullableFloat qtdeMaterial, qtdeProporcionalMaterial, custoUnitario;
                    NullableNumeric codMaterialDireto;
                    
                    //Materiais diversos com cartões do orçamento utilizados
                    EXEC SQL
                    SELECT OPMD.TIPO_MATERIAL, OPMD.COD_MATERIAL, TMO.DESCRICAO, OPMD.QUANTIDADE, OPMD.UNIDADE_MEDIDA, OMD.CUSTO_UNITARIO
                    FROM   <tabela> OPMD
                    JOIN   <tabela> OP ON OP.NUM_OP = OPMD.NUM_OP
                    JOIN   <tabela> OMD ON OMD.NUM_CONTROLE = OP.NUM_CONTROLE_ORCAM AND OMD.COD_MATERIAL = OPMD.COD_MATERIAL
                    JOIN   <tabela> TMO ON TMO.COD_MATERIAL_ORCAM = OPMD.COD_MATERIAL
                    WHERE OPMD.NUM_OP = :numOp
                    AND   OPMD.COD_EMBALAGEM = :codEmbalagem
                    AND   OPMD.COD_MATERIAL IS NOT NULL 
                    AND   OPMD.TIPO_MATERIAL !='ACO'
                    AND   NOT EXISTS ( SELECT 1 FROM <tabela> OA1 WHERE OA1.NUM_CONTROLE = OMD.NUM_CONTROLE AND OA1.COD_MATERIAL_ORCAM_1 = OMD.COD_MATERIAL )
                    UNION ALL
                    SELECT OPMD.TIPO_MATERIAL, OPMD.COD_MATERIAL, TMO.DESCRICAO, OPMD.QUANTIDADE, OPMD.UNIDADE_MEDIDA, OA.CUSTO_UNITARIO
                    FROM <tabela> OPMD
                    JOIN <tabela> TMO ON TMO.COD_MATERIAL_ORCAM = OPMD.COD_MATERIAL
                    JOIN <tabela> OP ON OP.NUM_OP = OPMD.NUM_OP
                    JOIN <tabela> OA ON OA.NUM_CONTROLE = OP.NUM_CONTROLE_ORCAM AND OA.COD_EMBALAGEM = OPMD.COD_EMBALAGEM AND OA.COD_MATERIAL = OPMD.COD_MATERIAL
                    WHERE OPMD.NUM_OP = :numOp
                    AND OPMD.COD_EMBALAGEM = :codEmbalagem
                    AND OPMD.TIPO_MATERIAL = 'ACO'
                    /* ocultado */
                    INTO tipoMaterial, codMaterialDireto, descMaterial, qtdeMaterial, unidadeMedMaterial, custoUnitario
                    EXECUTING
                    {
                            //Calcula quantidade proporcional do material
                            qtdeProporcionalMaterial = ((float)emElaboracao * qtdeMaterial) / (float)quantidadeOp;
                           
                            //Insere a quantidade proporcional do material na tabela temporária
                            EXEC SQL
                            INSERT INTO <tabela> VALUES(
                                :codEmpSel, :numOp, :codEmbalagem, :tipoMaterial, :codMaterialDireto, 
                                :descMaterial, :qtdeMaterial, :unidadeMedMaterial, :qtdeProporcionalMaterial, :custoUnitario );
                            
                    }
                    
                    
                    //Cartões utilizados
                    EXEC SQL
                    SELECT "CAR" AS TIPO, CPM.COD_PRODUTO_MATERIAL, CPM.DESCRICAO,  
                            IFNULL(( 	SELECT  SUM(EMM.QUANTIDADE_MOVIMENTADA) 
                                    FROM   <tabela> EMM 
                                    WHERE  EMM.COD_EMPRESA = RMI.COD_EMPRESA
                                    AND    EMM.NUM_REQUISICAO =   RMI.NUM_REQUISICAO
                                    AND    EMM.NUM_ITEM_REQUISICAO =  RMI.NUM_ITEM
                                    AND    EMM.ORIGEM_MOVTO = 'RQ'
                                    AND    EMM.COD_OPERACAO = 'SP'
                            ),0)
                            -
                            IFNULL(( 	SELECT SUM(EMM.QUANTIDADE_MOVIMENTADA) 
                                    FROM   <tabela> EMM 
                                    WHERE  EMM.COD_EMPRESA = RMI.COD_EMPRESA
                                    AND    EMM.NUM_REQUISICAO = RMI.NUM_REQUISICAO
                                    AND    EMM.NUM_ITEM_REQUISICAO = RMI.NUM_ITEM
                                    AND    EMM.ORIGEM_MOVTO = 'DV'
                                    AND    EMM.COD_OPERACAO = 'RP'
                            ),0) AS QUANTIDADE_UTILIZADA,
                    
                            CPM.UNIDADE,
                    
                            (SELECT EMM1.PRECO_MEDIO FROM ESTOQUE_MOVTO_MENSAL EMM1 WHERE EMM1.COD_MATERIAL = RMI.COD_MATERIAL  AND EMM1.PRECO_MEDIO > 0  ORDER BY EMM1.NUM_CONTROLE DESC LIMIT 1 ) AS PRECO_MEDIO
                    
                    FROM  /* ocultado */
                    INTO  tipoMaterial, codMaterialDireto, descMaterial, qtdeMaterial, unidadeMedMaterial, custoUnitario
                    EXECUTING
                    {
                            //Calcula quantidade proporcional do material
                            qtdeProporcionalMaterial = ((float)emElaboracao * qtdeMaterial) / (float)qtdeElabEstimada;
                            
                            
                            if(qtdeProporcionalMaterial>qtdeMaterial)  qtdeProporcionalMaterial = qtdeMaterial; // corrigige consumo por proporção
                            
                            EXEC SQL
                            INSERT INTO <TABELA> VALUES(
                                :codEmpSel, :numOp, :codEmbalagem, :tipoMaterial, :codMaterialDireto, 
                                :descMaterial, :qtdeMaterial, :unidadeMedMaterial, :qtdeProporcionalMaterial, :custoUnitario );
                    }*/
                                        
            }
            
            
            session.commitTransaction();
            
            
            /* ocultado */
        }
    }
    
    FIELD codEmpSel
    {
    
    }
}
