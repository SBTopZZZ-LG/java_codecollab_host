package components;

import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;

public abstract class PropertyChangeSupported {
    protected final PropertyChangeSupport support;

    public PropertyChangeSupported() {
        support = new PropertyChangeSupport(this);
    }

    public void addPropertyChangeListener(PropertyChangeListener pcl) {
        support.addPropertyChangeListener(pcl);
    }

    public void removePropertyChangeListener(PropertyChangeListener pcl) {
        support.removePropertyChangeListener(pcl);
    }

    protected void notifyListeners(final String tag, final Object oldValue, final Object newValue) {
        support.firePropertyChange(tag, oldValue, newValue);
    }
}
