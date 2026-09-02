package lk.icbt.dentalclinic.dao;

import lk.icbt.dentalclinic.model.HelpTopic;
import lk.icbt.dentalclinic.model.RoleCode;

import java.util.List;

/** Backs the help section - brief requirement 5. */
public interface HelpTopicDao {

    List<HelpTopic> findAllOrdered();

    List<HelpTopic> findVisibleTo(RoleCode role);
}
