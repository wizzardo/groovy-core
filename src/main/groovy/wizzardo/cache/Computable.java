package groovy.wizzardo.cache;

/**
 * @author: wizzardo
 * Date: 2/12/14
 */
public interface Computable<K, V> {

    public V compute(K k);
}
