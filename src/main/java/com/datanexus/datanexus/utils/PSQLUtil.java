package com.datanexus.datanexus.utils;

import lombok.extern.slf4j.Slf4j;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.Transaction;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.Query;
import jakarta.persistence.TypedQuery;
import java.sql.ResultSetMetaData;
import java.util.*;

@Component
@Slf4j
public class PSQLUtil {

    private static SessionFactory sessionFactory;
    private static JdbcTemplate jdbcTemplate;

    @Autowired
    public PSQLUtil(EntityManagerFactory entityManagerFactory, JdbcTemplate jdbcTemplate) {
        PSQLUtil.sessionFactory = entityManagerFactory.unwrap(SessionFactory.class);
        PSQLUtil.jdbcTemplate = jdbcTemplate;
    }

    public static List<Map<String, Object>> runNativeQueryForListOfMap(String sql) {
        try {
            return jdbcTemplate.query(sql, rs -> {
                if (!rs.isBeforeFirst()) return Collections.emptyList();
                List<Map<String, Object>> rows = new ArrayList<>();
                ResultSetMetaData md = rs.getMetaData();
                while (rs.next()) {
                    Map<String, Object> m = new LinkedHashMap<>();
                    for (int i = 1; i <= md.getColumnCount(); i++) {
                        m.put(md.getColumnLabel(i), rs.getObject(i));
                    }
                    rows.add(m);
                }
                return rows;
            });
        } catch (Exception e) {
            log.error("Error in runNativeQueryForListOfMap(): ", e);
            return Collections.emptyList();
        }
    }

    public static <T> boolean saveOrUpdate(T entity) {
        if (entity == null) return false;
        Transaction tx = null;
        try (Session session = sessionFactory.openSession()) {
            tx = session.beginTransaction();
            session.merge(entity);
            tx.commit();
            return true;
        } catch (Exception e) {
            if (tx != null) tx.rollback();
            log.error("Error in saveOrUpdate(): ", e);
            return false;
        }
    }

    public static <T> T saveOrUpdateWithReturn(T entity) {
        if (entity == null) return null;
        Transaction tx = null;
        try (Session session = sessionFactory.openSession()) {
            tx = session.beginTransaction();
            T merged = session.merge(entity);
            tx.commit();
            return merged;
        } catch (Exception e) {
            if (tx != null) tx.rollback();
            log.error("Error in saveOrUpdateWithReturn(): ", e);
            return null;
        }
    }

    public static <T> boolean saveOrUpdateList(Collection<T> list, int flushSize) {
        if (list == null || list.isEmpty()) return false;
        Transaction tx = null;
        try (Session session = sessionFactory.openSession()) {
            tx = session.beginTransaction();
            int i = 0;
            for (T obj : list) {
                session.merge(obj);
                if (flushSize > 0 && i % flushSize == 0) {
                    session.flush();
                    session.clear();
                }
                i++;
            }
            tx.commit();
            return true;
        } catch (Exception e) {
            if (tx != null) tx.rollback();
            log.error("Error in saveOrUpdateList(): ", e);
            return false;
        }
    }

    public static <T> List<T> runQuery(String hql, Map<String, Object> params,
                                       Class<T> clazz, Integer limit, Integer offset) {
        try (Session session = sessionFactory.openSession()) {
            TypedQuery<T> qry = session.createQuery(hql, clazz);
            if (params != null) params.forEach(qry::setParameter);
            if (limit != null) qry.setMaxResults(limit);
            if (offset != null) qry.setFirstResult(offset);
            return qry.getResultList();
        } catch (Exception e) {
            log.error("Error in runQuery(): ", e);
            return Collections.emptyList();
        }
    }

    public static <T> boolean runQueryForUpdate(String hql, Map<String, Object> params) {
        Transaction tx = null;
        try (Session session = sessionFactory.openSession()) {
            tx = session.beginTransaction();
            Query q = session.createQuery(hql);
            if (params != null) params.forEach(q::setParameter);
            q.executeUpdate();
            tx.commit();
            return true;
        } catch (Exception e) {
            if (tx != null) tx.rollback();
            log.error("Error in runQueryForUpdate(): ", e);
            return false;
        }
    }

    public static <T> boolean deleteList(Collection<T> objList, int flushSize) {
        if (objList == null || objList.isEmpty()) {
            return false;
        }

        Transaction tx = null;
        try (Session session = sessionFactory.openSession()) {
            tx = session.beginTransaction();

            int i = 0;
            for (T obj : objList) {
                session.remove(obj);
                if (flushSize > 0 && (i % flushSize) == 0) {
                    session.flush();
                    session.clear();
                }
                i++;
            }

            tx.commit();
            return true;
        } catch (Exception e) {
            if (tx != null) tx.rollback();
            log.error("Exception in deleteList(): ", e);
            return false;
        }
    }

    public static <T> List<T> runQuery(String hql, Map<String, Object> params, Class<T> clazz) {
        return runQuery(hql, params, clazz, null, null);
    }

    public static <T> T getSingleResult(List<T> list) {
        if (list != null && !list.isEmpty()) {
            return list.get(0);
        }
        return null;
    }

    public static <T> T getSingleResult(String queryString, Map<String, Object> parametersToSet, Class<T> className) {
        return getSingleResult(runQuery(queryString, parametersToSet, className, 1, 0));
    }
}
