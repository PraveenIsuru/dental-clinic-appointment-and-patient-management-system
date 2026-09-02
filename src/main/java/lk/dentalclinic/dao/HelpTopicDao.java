package lk.dentalclinic.dao;

import lk.dentalclinic.model.HelpTopic;
import lk.dentalclinic.model.RoleCode;

import java.util.List;

/** Backs the help section - brief requirement 5. */
public interface HelpTopicDao {

    List<HelpTopic> findAllOrdered();

    List<HelpTopic> findVisibleTo(RoleCode role);
}
