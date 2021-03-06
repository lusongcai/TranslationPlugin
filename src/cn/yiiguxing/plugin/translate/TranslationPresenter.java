package cn.yiiguxing.plugin.translate;

import cn.yiiguxing.plugin.translate.model.QueryResult;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.ref.Reference;
import java.lang.ref.WeakReference;
import java.util.List;

public class TranslationPresenter implements TranslationContract.Presenter {

    private final AppStorage mAppStorage;
    private final Settings mSettings;

    private final Translator mTranslator;
    private final TranslationContract.View mTranslationView;

    private String mCurrentQuery;

    public TranslationPresenter(@NotNull TranslationContract.View view) {
        mTranslator = Translator.getInstance();
        mTranslationView = view;
        mAppStorage = AppStorage.getInstance();
        mSettings = Settings.getInstance();
    }

    @NotNull
    @Override
    public List<String> getHistory() {
        return mAppStorage.getHistories();
    }

    @Nullable
    @Override
    public QueryResult getCache(String query) {
        if (Utils.isEmptyOrBlankString(query))
            return null;

        final Lang langFrom = Utils.notNull(mSettings.getLangFrom(), Lang.AUTO);
        final Lang langTo = Utils.notNull(mSettings.getLangTo(), Lang.AUTO);
        return mTranslator.getCache(new CacheKey(langFrom, langTo, query));
    }

    @Override
    public void query(@Nullable String query) {
        if (Utils.isEmptyOrBlankString(query) || query.equals(mCurrentQuery))
            return;

        query = query.trim();
        mAppStorage.addHistory(query);

        mCurrentQuery = query;

        // 防止内存泄漏
        final Reference<TranslationPresenter> presenterRef = new WeakReference<TranslationPresenter>(this);
        mTranslator.query(query, new Translator.Callback() {
            @Override
            public void onQuery(final String query, final QueryResult result) {
                ApplicationManager.getApplication().invokeLater(new Runnable() {
                    @Override
                    public void run() {
                        TranslationPresenter presenter = presenterRef.get();
                        if (presenter != null) {
                            presenter.onPostResult(query, result);
                        }
                    }
                }, ModalityState.any());
            }
        });
    }

    private void onPostResult(String query, QueryResult result) {
        if (Utils.isEmptyOrBlankString(query) || !query.equals(mCurrentQuery))
            return;

        mCurrentQuery = null;
        if (result.isSuccessful()) {
            mTranslationView.showResult(query, result);
        } else {
            String msg = result.getMessage();
            mTranslationView.showError(query, Utils.isEmptyOrBlankString(msg) ? "Nothing to show" : msg);
        }
    }
}
