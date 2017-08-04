package br.com.fornecedores.dao;

import br.com.fornecedores.model.Usuario;
import br.com.fornecedores.util.HibernateUtil;
import java.util.List;
import org.apache.log4j.Logger;
import org.hibernate.HibernateException;
import org.hibernate.Session;
import org.hibernate.criterion.Restrictions;

public class UsuarioInterfaceImpl implements UsuarioInterface {

    private static final Logger LOGGER = Logger.getLogger(UsuarioInterfaceImpl.class);

    @Override
    public Usuario buscarUsuario(String email, String senha) {

        Session session = HibernateUtil.getSessionFactory().getCurrentSession();
        boolean sessao = false;
        if (session == null) {
            session = HibernateUtil.getSessionFactory().openSession();
            sessao = true;
        }

        Usuario user = null;

        try {
            session.beginTransaction();
            if (senha != null) {
                user = (Usuario) session.createCriteria(Usuario.class)
                        .add(Restrictions.eq("email", email))
                        .add(Restrictions.eq("senha", senha)).uniqueResult();
            } else {
                user = (Usuario) session.createCriteria(Usuario.class)
                        .add(Restrictions.eq("email", email)).uniqueResult();
            }
            session.getTransaction().commit();
            return user;
        } catch (Exception e) {
            LOGGER.error(e.toString());
        } finally {
            if (sessao) {
                session.close();
            }
        }
        return null;
    }

    /**
     *
     * @return lista de usuários do tipo <code>Fornecedor</code>
     */
    @Override
    public List<Usuario> list() {

        Session session = HibernateUtil.getSessionFactory().getCurrentSession();
        boolean sessao = false;
        if (session == null) {
            session = HibernateUtil.getSessionFactory().openSession();
            sessao = true;
        }
        List<Usuario> lista = null;
        try {
            session.beginTransaction();
            lista = session.createQuery("From Usuario where tipo = 'F'").list();
            session.getTransaction().commit();
        } catch (Exception e) {
            LOGGER.error(e.toString());
            session.getTransaction().rollback();
        } finally {
            if (sessao) {
                session.close();
            }
        }
        return lista;

    }

    @Override
    public void adicionarUsuario(Usuario usuario) throws HibernateException{

        Session session = HibernateUtil.getSessionFactory().getCurrentSession();
        boolean sessao = false;
        if (session == null) {
            session = HibernateUtil.getSessionFactory().openSession();
            sessao = true;
        }

//        try {
            session.beginTransaction();
            session.save(usuario);
            session.getTransaction().commit();
        
            if (sessao) {
                session.close();
            }
            
    }

    @Override
    public void alterarUsuario(Usuario usuario) {

        Session session = HibernateUtil.getSessionFactory().getCurrentSession();
        boolean sessao = false;
        if (session == null) {
            session = HibernateUtil.getSessionFactory().openSession();
            sessao = true;
        }

        try {
            session.beginTransaction();
            session.update(usuario);
            session.getTransaction().commit();
        } catch (Exception e) {
            LOGGER.error(e.toString());
            session.getTransaction().rollback();
        } finally {
            if (sessao) {
                session.close();
            }
        }


    }

    @Override
    public void excluirUsuario(Usuario usuario) {

        Session session = HibernateUtil.getSessionFactory().getCurrentSession();
        boolean sessao = false;
        if (session == null) {
            session = HibernateUtil.getSessionFactory().openSession();
            sessao = true;
        }

        try {
            session.beginTransaction();
            session.delete(usuario);
            session.getTransaction().commit();
        } catch (Exception e) {
            LOGGER.error(e.toString());
            session.getTransaction().rollback();
        } finally {
            if (sessao) {
                session.close();
            }
        }
    }
}
