package org.zanata.model;

import java.io.Serializable;
import java.util.Objects;

import javax.persistence.Access;
import javax.persistence.AccessType;
import javax.persistence.Embeddable;
import javax.persistence.EmbeddedId;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.Table;
import javax.persistence.Transient;

/**
 * @author Alex Eng <a href="mailto:aeng@redhat.com">aeng@redhat.com</a>
 */
@Entity
@Table(name = "HProject_Glossary")
public class HProjectGlossary implements Serializable {
    private static final long serialVersionUID = -3353891750258196418L;
    private HProjectGlossaryPk id = new HProjectGlossaryPk();

    public HProjectGlossary(Glossary glossary, HProject project) {
        id.setGlossary(glossary);
        id.setProject(project);
    }

    @EmbeddedId
    protected HProjectGlossaryPk getId() {
        return id;
    }

    protected void setId(HProjectGlossaryPk id) {
        this.id = id;
    }

    @Transient
    public HProject getProject() {
        return id.getProject();
    }

    @Transient
    public Glossary getGlossary() {
        return id.getGlossary();
    }

    @Embeddable
    @Access(AccessType.FIELD)
    public static class HProjectGlossaryPk implements Serializable {
        private static final long serialVersionUID = -3849528527085874091L;
        @ManyToOne(fetch = FetchType.EAGER, optional = false)
        @JoinColumn(name = "glossaryId", nullable = false)
        private Glossary glossary;
        @ManyToOne(fetch = FetchType.EAGER, optional = false)
        @JoinColumn(name = "projectId", nullable = false)
        private HProject project;

        public void setGlossary(final Glossary glossary) {
            this.glossary = glossary;
        }

        public void setProject(final HProject project) {
            this.project = project;
        }

        public Glossary getGlossary() {
            return this.glossary;
        }

        public HProject getProject() {
            return this.project;
        }

        @java.beans.ConstructorProperties({ "glossary", "project" })
        public HProjectGlossaryPk(final Glossary glossary,
                final HProject project) {
            this.glossary = glossary;
            this.project = project;
        }

        public HProjectGlossaryPk() {
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            HProjectGlossaryPk that = (HProjectGlossaryPk) o;
            return Objects.equals(glossary, that.glossary) &&
                    Objects.equals(project, that.project);
        }

        @Override
        public int hashCode() {
            return Objects.hash(glossary, project);
        }
    }

    public HProjectGlossary() {
    }
}
