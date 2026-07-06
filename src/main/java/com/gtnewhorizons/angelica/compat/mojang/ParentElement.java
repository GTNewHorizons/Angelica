package com.gtnewhorizons.angelica.compat.mojang;

import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.ListIterator;
import java.util.Optional;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

public interface ParentElement extends Element {
    List<? extends Element> children();

    default Optional<Element> hoveredElement(double mouseX, double mouseY) {
        for(Element element : this.children()) {
            if (element.isMouseOver(mouseX, mouseY)) {
                return Optional.of(element);
            }
        }
        return Optional.empty();
    }

    default boolean mouseClicked(double mouseX, double mouseY, int button) {
        for(Element element : this.children()) {
            if (element.mouseClicked(mouseX, mouseY, button)) {
                this.setFocused(element);
                if (button == 0) {
                    this.setDragging(true);
                }
                return true;
            }
        }
        return false;
    }

    default boolean mouseReleased(double mouseX, double mouseY, int button) {
        this.setDragging(false);
        return this.hoveredElement(mouseX, mouseY).filter((element) -> element.mouseReleased(mouseX, mouseY, button)).isPresent();
    }

    default boolean mouseDragged(double mouseX, double mouseY, int button) {
        return this.getFocused() != null && this.isDragging() && button == 0 && this.getFocused().mouseDragged(mouseX, mouseY, button);
    }

    boolean isDragging();

    void setDragging(boolean dragging);

    default boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        return this.hoveredElement(mouseX, mouseY).filter((element) -> element.mouseScrolled(mouseX, mouseY, amount)).isPresent();
    }

    default boolean keyTyped(char typedChar, int keyCode) {
        return this.getFocused() != null && this.getFocused().keyTyped(typedChar, keyCode);
    }

    @Nullable
    Element getFocused();

    void setFocused(@Nullable Element focused);

    default boolean changeFocus(boolean lookForwards) {
        final Element element = this.getFocused();
        if (element != null && element.changeFocus(lookForwards)) {
            return true;
        } else {
            final List<? extends Element> list = this.children();
            final int i = list.indexOf(element);
            final int j;
            if (element != null && i >= 0) {
                j = i + (lookForwards ? 1 : 0);
            } else if (lookForwards) {
                j = 0;
            } else {
                j = list.size();
            }


            final ListIterator<? extends Element> listIterator = list.listIterator(j);
            final BooleanSupplier booleanSupplier = lookForwards ? listIterator::hasNext : listIterator::hasPrevious;
            final Supplier<? extends Element> supplier = lookForwards ? listIterator::next : listIterator::previous;

            Element element2;
            do {
                if (!booleanSupplier.getAsBoolean()) {
                    this.setFocused(null);
                    return false;
                }

                element2 = supplier.get();
            } while(!element2.changeFocus(lookForwards));

            this.setFocused(element2);
            return true;
        }
    }
}
