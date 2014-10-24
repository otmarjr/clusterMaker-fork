package clusterMaker.algorithms.numeric;

/**
 * Summarizer interface.
 * Required because Java does not support function pointers.
 * @author djh.shih
*/
public interface Summarizer {
	Double summarize(Double[] a);
}
