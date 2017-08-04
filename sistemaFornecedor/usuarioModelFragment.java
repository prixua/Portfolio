package br.com.fornecedores.model;

import br.com.fornecedores.dao.CadClientesFornecInterface;
import br.com.fornecedores.dao.CadClientesFornecInterfaceImpl;
import java.util.List;
import javax.persistence.CascadeType;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.OneToMany;
import javax.persistence.OneToOne;
import javax.persistence.Table;
import javax.persistence.UniqueConstraint;

@Entity
@Table(name = "TAB_FORNECEDORES_USUARIOS", catalog = "SISTEMAS", uniqueConstraints =
@UniqueConstraint(columnNames = "EMAIL"))
public class Usuario implements java.io.Serializable {

    @Column(name = "NUM_CONTROLE")
    @Id
    @GeneratedValue
    private int numControle;
    @Column(name = "EMAIL", unique = true, nullable = false, length = 50)
    private String email;
    @Column(name = "SENHA", nullable = false, length = 250)
    private String senha;
    @OneToOne
    @JoinColumn(name = "COD_FORNECEDOR")
    private CadClientesFornec fornecedor;
    @Column(name = "TIPO", length = 2, insertable = false, columnDefinition = "ENUM('A','F') default F")
    private String tipo;
    @Column(name = "ATIVO", length = 2, insertable = false, columnDefinition = "ENUM('N','S') default 'S'")
    private String ativo;
    @OneToMany(cascade = CascadeType.ALL, fetch = FetchType.EAGER, mappedBy = "usuario",   targetEntity = TabFornecedoresUsuariosEmpresas.class)
    private List<TabFornecedoresUsuariosEmpresas> tabFornecedoresUsuariosEmpresasSet;

    
    
    public Usuario() {
    }

    public Usuario(String email, String senha, int codFornecedor) {
        this.email = email;
        this.senha = senha;
        CadClientesFornecInterface dao = new CadClientesFornecInterfaceImpl();
        this.fornecedor = dao.buscarCodCliente(codFornecedor);
    }

    public int getNumControle() {
        return this.numControle;
    }

    public void setNumControle(int numControle) {
        this.numControle = numControle;
    }

    public String getEmail() {
        return this.email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getSenha() {
        return this.senha;
    }

    public void setSenha(String senha) {
        this.senha = senha;
    }

    public String getTipo() {
        return this.tipo;
    }

    public void setTipo(String tipo) {
        this.tipo = tipo;
    }

    public String getAtivo() {
        return ativo;
    }

    public void setAtivo(String ativo) {
        this.ativo = ativo;
    }

    public CadClientesFornec getFornecedor() {
        return fornecedor;
    }

    public void setFornecedor(CadClientesFornec fornecedor) {
        this.fornecedor = fornecedor;
    }


    
    public List<TabFornecedoresUsuariosEmpresas> getTabFornecedoresUsuariosEmpresasSet() {
        return tabFornecedoresUsuariosEmpresasSet;
    }

    public void setTabFornecedoresUsuariosEmpresasSet(List<TabFornecedoresUsuariosEmpresas> tabFornecedoresUsuariosEmpresasSet) {
        this.tabFornecedoresUsuariosEmpresasSet = tabFornecedoresUsuariosEmpresasSet;
    }

    @Override
    public String toString() {
        return "Usuario{" + "numControle=" + numControle + ", email=" + email + ", senha=" + senha + ", fornecedor=" + fornecedor + ", tipo=" + tipo + ", ativo=" + ativo + ", tabFornecedoresUsuariosEmpresasSet=" + tabFornecedoresUsuariosEmpresasSet + "\r\n" + '}';
    }

    @Override
    public int hashCode() {
        int hash = 3;
        hash = 29 * hash + this.numControle;
        hash = 29 * hash + (this.email != null ? this.email.hashCode() : 0);
        hash = 29 * hash + (this.senha != null ? this.senha.hashCode() : 0);
        hash = 29 * hash + (this.tipo != null ? this.tipo.hashCode() : 0);
        return hash;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final Usuario other = (Usuario) obj;
        if (this.numControle != other.numControle) {
            return false;
        }
        if ((this.email == null) ? (other.email != null) : !this.email.equals(other.email)) {
            return false;
        }
        if ((this.senha == null) ? (other.senha != null) : !this.senha.equals(other.senha)) {
            return false;
        }
        if ((this.tipo == null) ? (other.tipo != null) : !this.tipo.equals(other.tipo)) {
            return false;
        }
        return true;
    }
}
