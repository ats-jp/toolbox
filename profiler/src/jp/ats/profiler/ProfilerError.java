package jp.ats.profiler;

public class ProfilerError extends Error {

	private static final long serialVersionUID = -6476468977686594782L;

	ProfilerError(Exception e) {
		super(e);
	}
}
