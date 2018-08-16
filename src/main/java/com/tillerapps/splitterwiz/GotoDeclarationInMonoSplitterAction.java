package com.tillerapps.splitterwiz;

import java.util.Arrays;
import javax.swing.SwingConstants;

import com.intellij.codeInsight.TargetElementUtil;
import com.intellij.codeInsight.navigation.actions.GotoDeclarationAction;
import com.intellij.ide.util.EditSourceUtil;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx;
import com.intellij.openapi.fileEditor.impl.EditorWindow;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.Navigatable;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Created by Kenneth Tiller on 15/08/18.
 */
public class GotoDeclarationInMonoSplitterAction extends AnAction {

    private final GotoDeclarationAction fallbackGotoDeclarationAction = new GotoDeclarationAction();

    public void actionPerformed(AnActionEvent e) {
        final PsiElement element = getTarget(e);
        if (element == null) {
            fallback(e);
            return;
        }

        final PsiElement navElement = TargetElementUtil.getInstance()
                .getGotoDeclarationTarget(element, element.getNavigationElement());
        if (navElement == null) {
            fallback(e);
            return;
        }

        final Project project = PlatformDataKeys.PROJECT.getData(e.getDataContext());
        if (project == null) {
            return;
        }
        final FileEditorManagerEx fileEditorManager = FileEditorManagerEx.getInstanceEx(project);
        final EditorWindow editorWindow2 = editorWindow2(project, fileEditorManager);

        fileEditorManager.setCurrentWindow(editorWindow2);
        gotoTargetElement(navElement);

        PsiFile containingFile = navElement.getContainingFile();
        if (containingFile == null) {
            // we have navigated to something that is not a file nad that's a-ok
            return;
        }
        VirtualFile newViewingFile = containingFile.getVirtualFile();
        Arrays.stream(editorWindow2.getFiles()).forEach(file -> {
            if (!file.equals(newViewingFile)) {
                editorWindow2.closeFile(file);
            }
        });
        editorWindow2.requestFocus(true);
    }

    private void fallback(AnActionEvent e) {
        this.fallbackGotoDeclarationAction.actionPerformed(e);
    }

    private static void gotoTargetElement(@NotNull PsiElement element) {
        Navigatable navigatable = element instanceof Navigatable ? (Navigatable) element : EditSourceUtil.getDescriptor(element);
        if (navigatable != null && navigatable.canNavigate()) {
            navigatable.navigate(true);
        }
    }

    @Override
    public void update(AnActionEvent e) {
        PsiElement target = getTarget(e);
        if (target != null) {
            e.getPresentation().setEnabled(true);
        } else {
            this.fallbackGotoDeclarationAction.update(e);
        }
    }

    private EditorWindow editorWindow2(Project project,
                                       FileEditorManagerEx fileEditorManager) {

        EditorWindow[] windows = fileEditorManager.getWindows();
        if (windows.length > 1) {
            return windows[1];
        }

        EditorWindow firstWindowPane = windows[0];

        FileEditorManagerEx fileManagerEx = (FileEditorManagerEx) FileEditorManagerEx.getInstance(project);
        fileManagerEx.createSplitter(SwingConstants.VERTICAL, fileManagerEx.getCurrentWindow());
        return fileEditorManager.getNextWindow(firstWindowPane);
    }

    @Nullable
    private PsiElement getTarget(AnActionEvent e) {
        PsiFile psiFile = e.getData(LangDataKeys.PSI_FILE);
        Editor editor = e.getData(PlatformDataKeys.EDITOR);

        if (psiFile == null || editor == null) {
            e.getPresentation().setEnabled(false);
            return null;
        }

        int offset = editor.getCaretModel().getOffset();
        PsiElement elementAt = psiFile.findElementAt(offset);

        if (elementAt == null) {
            e.getPresentation().setEnabled(false);
            return null;
        }

        PsiElement[] allTargetElements = GotoDeclarationAction.findAllTargetElements(elementAt.getProject(), editor, offset);
        return allTargetElements.length > 0 ? allTargetElements[0] : null;
    }
}
