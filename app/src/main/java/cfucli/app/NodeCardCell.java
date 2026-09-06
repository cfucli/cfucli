package cfucli.app;

import javafx.scene.control.ListCell;
import luvjfx.FxLabel;

import static luvjfx.Fx.*;

/** One node, rendered. A top-level class rather than an anonymous cell factory so it stays
 *  readable and stays under its own roof. */
public final class NodeCardCell extends ListCell<NodeCard> {

    final FxLabel title = label("").styleClass("card-title");
    final FxLabel detail = label("").styleClass("card-detail");
    final FxLabel state = Ui.pill("", "pill-wait");
    final FxLabel window = Ui.pill("", "pill-view");

    final javafx.scene.Node graphic = hbox($ -> $.spacing(10)).nodes(
            vbox($ -> $.spacing(2)).nodes(title, detail)).node;

    public NodeCardCell() {
        var row = (javafx.scene.layout.HBox) graphic;
        row.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        row.getChildren().add(Ui.spacer());
        row.getChildren().addAll(window.node, state.node);
        setGraphic(null);
    }

    @Override
    protected void updateItem(NodeCard card, boolean empty) {
        super.updateItem(card, empty);
        if (empty || card == null) {
            setText(null);
            setGraphic(null);
            return;
        }
        title.text(NodeScan.title(card));
        detail.text(card.detail() == null ? "" : card.detail());
        state.text(NodeScan.pill(card)).styleClass("pill", NodeScan.pillStyle(card));
        var hasWindow = Boolean.TRUE.equals(card.hasWindow());
        window.visible(true).text(hasWindow
                ? (Boolean.TRUE.equals(card.windowVisible()) ? "window open" : "window hidden")
                : "headless");
        window.styleClass("pill", hasWindow ? "pill-view" : "");
        setText(null);
        setGraphic(graphic);
    }
}
